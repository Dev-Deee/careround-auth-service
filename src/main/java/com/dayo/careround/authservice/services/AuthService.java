package com.dayo.careround.authservice.services;

import com.dayo.careround.authservice.config.JwtProperties;
import com.dayo.careround.authservice.data.models.AuthUser;
import com.dayo.careround.authservice.data.models.OutboxEvent;
import com.dayo.careround.authservice.data.models.RefreshToken;
import com.dayo.careround.authservice.data.models.enums.UserStatus;
import com.dayo.careround.authservice.data.repositories.AuthUserRepository;
import com.dayo.careround.authservice.data.repositories.OutboxEventRepository;
import com.dayo.careround.authservice.data.repositories.RefreshTokenRepository;
import com.dayo.careround.authservice.dto.request.ChangePasswordRequest;
import com.dayo.careround.authservice.dto.request.LoginRequest;
import com.dayo.careround.authservice.dto.request.RefreshRequest;
import com.dayo.careround.authservice.dto.request.RegisterRequest;
import com.dayo.careround.authservice.dto.response.AuthResponse;
import com.dayo.careround.authservice.exception.AccountNotActiveException;
import com.dayo.careround.authservice.exception.InvalidCredentialsException;
import com.dayo.careround.authservice.exception.TokenException;
import com.dayo.careround.authservice.exception.UserAlreadyExistsException;
import com.dayo.careround.authservice.utils.AuthMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final AuthUserRepository authUserRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final JwtProperties jwtProperties;
    private final BCryptPasswordEncoder passwordEncoder;

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
                user.getId(),
                user.getRole().name(),
                user.getGrade(),
                user.getAssignedWardId()
        );

        String rawRefreshToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken refreshToken = AuthMapper.toRefreshToken(
                tokenHash, user.getId(), user.getRole().name(), jwtProperties.getRefreshTokenExpiryMs());
        refreshTokenRepository.save(refreshToken);

        return AuthMapper.toAuthResponse(accessToken, rawRefreshToken, jwtProperties.getAccessTokenExpiryMs());
    }

    public void register(RegisterRequest request) {
        if (authUserRepository.existsByEmail(request.email())) {
            throw new UserAlreadyExistsException("Email already registered");
        }
        if (authUserRepository.existsByPhoneNumber(request.phoneNumber())) {
            throw new UserAlreadyExistsException("Phone number already registered");
        }

        AuthUser user = new AuthUser();
        user.setEmail(request.email());
        user.setPhoneNumber(request.phoneNumber());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setRole(request.role());
        user.setGrade(request.grade());
        user.setAssignedWardId(request.assignedWardId());
        authUserRepository.save(user);

        String payload = buildPayload(user, request);
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setIdempotencyKey(user.getId() + "-user-registered");
        outboxEvent.setEventType("user.registered");
        outboxEvent.setPayload(payload);
        outboxEventRepository.save(outboxEvent);
    }

    public AuthResponse refresh(RefreshRequest request) {
        String tokenHash = hashToken(request.refreshToken());

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new TokenException("Invalid refresh token"));

        if (stored.isRevoked()) {
            throw new TokenException("Refresh token has been revoked");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new TokenException("Refresh token has expired");
        }

        stored.setRevoked(true);
        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);

        String newRawRefreshToken = UUID.randomUUID().toString();
        String newTokenHash = hashToken(newRawRefreshToken);

        RefreshToken newRefreshToken = AuthMapper.toRefreshToken(
                newTokenHash, stored.getUserId(), stored.getUserRole(), jwtProperties.getRefreshTokenExpiryMs());
        refreshTokenRepository.save(newRefreshToken);

        AuthUser user = authUserRepository.findById(stored.getUserId())
                .orElseThrow(() -> new TokenException("User no longer exists"));

        String newAccessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getRole().name(),
                user.getGrade(),
                user.getAssignedWardId()
        );

        return AuthMapper.toAuthResponse(newAccessToken, newRawRefreshToken, jwtProperties.getAccessTokenExpiryMs());
    }

    public void logout(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
    }

    public void changePassword(UUID userId, ChangePasswordRequest request) {
        AuthUser user = authUserRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException());

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        authUserRepository.save(user);

        // Revoke all existing refresh tokens — force re-login everywhere
        refreshTokenRepository.deleteAllByUserId(userId);
    }

    private String buildPayload(AuthUser user, RegisterRequest request) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("id", user.getId().toString());
            payload.put("publicId", user.getPublicId().toString());
            payload.put("firstName", request.firstName());
            payload.put("lastName", request.lastName());
            payload.put("email", user.getEmail());
            payload.put("phoneNumber", user.getPhoneNumber());
            payload.put("role", user.getRole().name());
            payload.put("grade", user.getGrade());
            payload.put("assignedWardId", user.getAssignedWardId() != null ? user.getAssignedWardId().toString() : null);
            payload.put("specialization", request.specialization());
            payload.put("licenseNumber", request.licenseNumber());
            return new ObjectMapper().writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("Failed to build event payload", e);
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