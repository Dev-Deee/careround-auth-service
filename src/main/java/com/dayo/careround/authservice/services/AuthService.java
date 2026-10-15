package com.dayo.careround.authservice.services;

import com.dayo.careround.authservice.config.JwtProperties;
import com.dayo.careround.authservice.data.models.AuthUser;
import com.dayo.careround.authservice.data.models.OutboxEvent;
import com.dayo.careround.authservice.data.models.RefreshToken;
import com.dayo.careround.authservice.data.models.VerificationToken;
import com.dayo.careround.authservice.data.models.enums.*;
import com.dayo.careround.authservice.data.repositories.AuthUserRepository;
import com.dayo.careround.authservice.data.repositories.OutboxEventRepository;
import com.dayo.careround.authservice.data.repositories.RefreshTokenRepository;
import com.dayo.careround.authservice.data.repositories.VerificationTokenRepository;
import com.dayo.careround.authservice.dto.request.*;
import com.dayo.careround.authservice.dto.response.AuthResponse;
import com.dayo.careround.authservice.exception.*;
import com.dayo.careround.authservice.grpc.LicenseVerificationClient;
import com.dayo.careround.authservice.utils.AuthMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final AuthUserRepository authUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JwtProperties jwtProperties;
    private final BCryptPasswordEncoder passwordEncoder;
    private final StringRedisTemplate redisTemplate;
    private final LicenseVerificationClient licenseVerificationClient;
    private final ObjectMapper objectMapper;


    // ---------------------------------------------------------------
    // LOGIN
    // ---------------------------------------------------------------

    public AuthResponse login(LoginRequest request) {
        AuthUser user = authUserRepository
                .findByEmailOrPhoneNumber(request.identifier(), request.identifier())
                .orElseThrow(InvalidCredentialsException::new);

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String accessToken = jwtService.generateAccessToken(
                user.getId(), user.getRole().name(), user.getGrade(),
                user.getAssignedWardId(), user.getPermissions());

        String rawRefreshToken = UUID.randomUUID().toString();
        RefreshToken refreshToken = AuthMapper.toRefreshToken(
                hashToken(rawRefreshToken), user.getId(), user.getRole().name(),
                jwtProperties.getRefreshTokenExpiryMs());
        refreshTokenRepository.save(refreshToken);

        return AuthMapper.toAuthResponse(accessToken, rawRefreshToken, jwtProperties.getAccessTokenExpiryMs());
    }

    // ---------------------------------------------------------------
    // REGISTER (admin-only)
    // ---------------------------------------------------------------

    @Transactional
    public void register(RegisterRequest request, AuthUser creator) {

        if (request.role() == UserRole.ADMIN) {
            if (!creator.getPermissions().contains(AdminPermission.GRANT_PERMISSIONS)) {
                throw new InsufficientPermissionException(
                        "Only admins with GRANT_PERMISSIONS can create other admins");
            }
            if (!creator.getPermissions().containsAll(request.permissions())) {
                throw new PrivilegeEscalationException("Cannot grant permissions you do not hold");
            }
        } else {
            if (!creator.getPermissions().contains(AdminPermission.CREATE_USER)) {
                throw new InsufficientPermissionException(
                        "You do not have permission to create staff accounts");
            }
        }

        if (authUserRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("Email already registered");
        }
        if (authUserRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new UserAlreadyExistsException("Phone number already registered");
        }
        if ((request.role() == UserRole.DOCTOR || request.role() == UserRole.NURSE)
                && licenseVerificationClient.licenseNumberExists(request.licenseNumber())) {
            throw new UserAlreadyExistsException("This license number is already registered");
        }

        AuthUser newUser = AuthMapper.toAuthUser(request);
        authUserRepository.save(newUser);
        issueRegistrationVerification(newUser, request);
    }

    // ---------------------------------------------------------------
    // ACTIVATE ACCOUNT
    // ---------------------------------------------------------------

    @Transactional
    public void activateAccount(String rawToken, String newPassword) {
        VerificationToken vt = findUsableActivationToken(rawToken);

        AuthUser user = authUserRepository.findById(vt.getUserId())
                .orElseThrow(() -> new InvalidVerificationTokenException("Invalid or expired verification token"));

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setStatus(UserStatus.ACTIVE);
        authUserRepository.save(user);

        vt.setUsed(true);
        verificationTokenRepository.save(vt);
    }

    // Used by the emailed link's set-password page: rejects unknown, used or expired tokens up front.
    public void checkActivationToken(String rawToken) {
        findUsableActivationToken(rawToken);
    }

    private VerificationToken findUsableActivationToken(String rawToken) {
        VerificationToken vt = verificationTokenRepository
                .findByTokenHashAndPurpose(hashToken(rawToken), VerificationTokenPurpose.ACCOUNT_ACTIVATION)
                .orElseThrow(() -> new InvalidVerificationTokenException("Invalid or expired verification token"));

        if (vt.isUsed() || vt.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidVerificationTokenException("Invalid or expired verification token");
        }
        return vt;
    }

    // ---------------------------------------------------------------
    // RESEND ACTIVATION
    // ---------------------------------------------------------------

    @Transactional
    public void resendActivation(String email) {
        AuthUser user = authUserRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("No user with that email"));

        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new InvalidStateException("Account is not pending verification");
        }

        verificationTokenRepository.invalidateAllForUserAndPurpose(user.getId(), VerificationTokenPurpose.ACCOUNT_ACTIVATION);
        issueResendVerification(user);
    }

    // ---------------------------------------------------------------
    // REFRESH
    // ---------------------------------------------------------------

    public AuthResponse refresh(RefreshRequest request) {
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hashToken(request.refreshToken()))
                .orElseThrow(() -> new TokenException("Invalid refresh token"));

        if (stored.isRevoked()) throw new TokenException("Refresh token has been revoked");
        if (stored.getExpiresAt().isBefore(Instant.now())) throw new TokenException("Refresh token has expired");

        stored.setRevoked(true);
        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);

        String newRawRefreshToken = UUID.randomUUID().toString();
        RefreshToken newRefreshToken = AuthMapper.toRefreshToken(
                hashToken(newRawRefreshToken), stored.getUserId(), stored.getUserRole(),
                jwtProperties.getRefreshTokenExpiryMs());
        refreshTokenRepository.save(newRefreshToken);

        AuthUser user = authUserRepository.findById(stored.getUserId())
                .orElseThrow(() -> new TokenException("User no longer exists"));

        String newAccessToken = jwtService.generateAccessToken(
                user.getId(), user.getRole().name(), user.getGrade(),
                user.getAssignedWardId(), user.getPermissions());

        return AuthMapper.toAuthResponse(newAccessToken, newRawRefreshToken, jwtProperties.getAccessTokenExpiryMs());
    }

    // ---------------------------------------------------------------
    // LOGOUT
    // ---------------------------------------------------------------

    public void logout(String rawRefreshToken) {
        refreshTokenRepository.findByTokenHash(hashToken(rawRefreshToken)).ifPresent(token -> {
            token.setRevoked(true);
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    // ---------------------------------------------------------------
    // CHANGE PASSWORD
    // ---------------------------------------------------------------

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        AuthUser user = authUserRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        authUserRepository.save(user);
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    // ---------------------------------------------------------------
    // CHANGE EMAIL
    // ---------------------------------------------------------------

    @Transactional
    public void changeEmail(UUID userId, ChangeEmailRequest request) {
        AuthUser user = authUserRepository.findById(userId)
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        if (authUserRepository.existsByEmail(request.newEmail())) {
            throw new UserAlreadyExistsException("Email already in use");
        }

        verificationTokenRepository.invalidateAllForUserAndPurpose(userId, VerificationTokenPurpose.EMAIL_CHANGE);

        user.setPendingEmail(request.newEmail());
        authUserRepository.save(user);
        issueEmailChangeVerification(user, request.newEmail());
    }

    @Transactional
    public void confirmEmailChange(String rawToken) {
        VerificationToken vt = verificationTokenRepository
                .findByTokenHashAndPurpose(hashToken(rawToken), VerificationTokenPurpose.EMAIL_CHANGE)
                .orElseThrow(() -> new InvalidVerificationTokenException("Invalid or expired verification token"));

        if (vt.isUsed() || vt.getExpiresAt().isBefore(Instant.now())) {
            throw new InvalidVerificationTokenException("Invalid or expired verification token");
        }

        AuthUser user = authUserRepository.findById(vt.getUserId())
                .orElseThrow(() -> new InvalidVerificationTokenException("Invalid or expired verification token"));

        if (user.getPendingEmail() == null) {
            throw new InvalidStateException("No pending email change for this account");
        }

        user.setEmail(user.getPendingEmail());
        user.setPendingEmail(null);
        authUserRepository.save(user);

        vt.setUsed(true);
        verificationTokenRepository.save(vt);
        refreshTokenRepository.deleteAllByUserId(user.getId());
    }

    private void issueEmailChangeVerification(AuthUser user, String newEmail) {
        String rawToken = UUID.randomUUID().toString();

        VerificationToken vt = new VerificationToken();
        vt.setUserId(user.getId());
        vt.setTokenHash(hashToken(rawToken));
        vt.setPurpose(VerificationTokenPurpose.EMAIL_CHANGE);
        vt.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        verificationTokenRepository.save(vt);

        log.info("EMAIL CHANGE CONFIRMATION LINK for {}: http://localhost:8081/api/v1/auth/confirm-email-change?token={}",
                newEmail, rawToken);
    }

    // ---------------------------------------------------------------
    // CANCEL AND DELETE PENDING ACCOUNT
    // ---------------------------------------------------------------

    @Transactional
    public void cancelPendingAccount(UUID userId) {
        AuthUser user = authUserRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("No user with that id"));

        if (user.getStatus() != UserStatus.PENDING_VERIFICATION) {
            throw new AccountNotCancellableException(
                    "Only a PENDING_VERIFICATION account can be cancelled — current status: " + user.getStatus());
        }

        verificationTokenRepository.deleteAllByUserId(userId);
        redisTemplate.delete("activation-token:" + userId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId.toString());

        outboxEventRepository.save(buildOutboxEvent(
                "user-registration-cancelled-" + userId,
                "user.registration_cancelled",
                serialize(payload)));

        authUserRepository.delete(user);
    }

    // ---------------------------------------------------------------
    // PRIVATE HELPERS — verification issuing (split events, mapper-driven)
    // ---------------------------------------------------------------

    private void issueRegistrationVerification(AuthUser user, RegisterRequest request) {
        String rawToken = createAndStashActivationToken(user, Duration.ofHours(1));
        outboxEventRepository.save(buildOutboxEvent(
                "user-registered-" + user.getId(),
                "user.registered",
                serialize(AuthMapper.toUserRegisteredPayload(user, request, rawToken))));
    }

    private void issueResendVerification(AuthUser user) {
        String rawToken = createAndStashActivationToken(user, Duration.ofMinutes(30));
        outboxEventRepository.save(buildOutboxEvent(
                "verification-resent-" + user.getId() + "-" + Instant.now().toEpochMilli(),
                "user.verification_resent",
                serialize(AuthMapper.toVerificationResentPayload(user, rawToken))));
    }

    private String createAndStashActivationToken(AuthUser user, Duration expiry) {
        String rawToken = UUID.randomUUID().toString();

        VerificationToken vt = new VerificationToken();
        vt.setUserId(user.getId());
        vt.setTokenHash(hashToken(rawToken));
        vt.setPurpose(VerificationTokenPurpose.ACCOUNT_ACTIVATION);
        vt.setExpiresAt(Instant.now().plus(expiry));
        verificationTokenRepository.save(vt);

        // Temporarily hold the RAW token in Redis, keyed by userId, so notification-service
        // can retrieve it via gRPC — the hash stored above can never be reversed back to this.
        redisTemplate.opsForValue().set(
                "activation-token:" + user.getId(),
                rawToken,
                expiry
        );

        return rawToken;
    }

    private OutboxEvent buildOutboxEvent(String idempotencyKey, String eventType, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.setIdempotencyKey(idempotencyKey);
        event.setEventType(eventType);
        event.setStatus(OutboxStatus.PENDING);
        event.setPayload(payload);
        event.setAttempts(0);
        event.setNextRetryAt(Instant.now());
        event.setCreatedAt(Instant.now());
        return event;
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize event payload", e);
        }
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Failed to hash token", e);
        }
    }
}