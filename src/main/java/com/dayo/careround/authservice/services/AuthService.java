package com.dayo.careround.authservice.services;

import com.dayo.careround.authservice.data.models.RefreshToken;
import com.dayo.careround.authservice.data.repositories.RefreshTokenRepository;
import com.dayo.careround.authservice.dto.request.LoginRequest;
import com.dayo.careround.authservice.dto.request.RefreshRequest;
import com.dayo.careround.authservice.dto.response.AuthResponse;
import com.dayo.careround.authservice.dto.response.UserCredentialResponse;
import com.dayo.careround.authservice.config.JwtProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtService jwtService;
    private final UserServiceClient userServiceClient;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final BCryptPasswordEncoder passwordEncoder;

    public AuthResponse login(LoginRequest request) {
        // 1. Fetch user from user-service
        UserCredentialResponse user = userServiceClient.findByIdentifier(request.getIdentifier());

        // 2. Check user exists and is active
        if (user == null) {
            throw new RuntimeException("Invalid credentials");
        }
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new RuntimeException("Account is not active");
        }

        // 3. Verify password — always run even if user not found to prevent timing attacks
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid credentials");
        }

        // 4. Generate access token
        String accessToken = jwtService.generateAccessToken(
                user.getId(),
                user.getRole(),
                user.getGrade(),
                user.getAssignedWardId()
        );

        // 5. Generate and store refresh token
        String rawRefreshToken = UUID.randomUUID().toString();
        String tokenHash = hashToken(rawRefreshToken);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setUserId(user.getId());
        refreshToken.setUserRole(user.getRole());
        refreshToken.setIssuedAt(Instant.now());
        refreshToken.setExpiresAt(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpiryMs()));
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(rawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpiryMs() / 1000)
                .build();
    }

    public AuthResponse refresh(RefreshRequest request) {
        String tokenHash = hashToken(request.getRefreshToken());

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new RuntimeException("Invalid refresh token"));

        if (stored.isRevoked()) {
            throw new RuntimeException("Refresh token has been revoked");
        }
        if (stored.getExpiresAt().isBefore(Instant.now())) {
            throw new RuntimeException("Refresh token has expired");
        }

        // Rotate — revoke old, issue new
        stored.setRevoked(true);
        stored.setRevokedAt(Instant.now());
        refreshTokenRepository.save(stored);

        String newRawRefreshToken = UUID.randomUUID().toString();
        String newTokenHash = hashToken(newRawRefreshToken);

        RefreshToken newRefreshToken = new RefreshToken();
        newRefreshToken.setTokenHash(newTokenHash);
        newRefreshToken.setUserId(stored.getUserId());
        newRefreshToken.setUserRole(stored.getUserRole());
        newRefreshToken.setIssuedAt(Instant.now());
        newRefreshToken.setExpiresAt(Instant.now().plusMillis(jwtProperties.getRefreshTokenExpiryMs()));
        refreshTokenRepository.save(newRefreshToken);

        String newAccessToken = jwtService.generateAccessToken(
                stored.getUserId(),
                stored.getUserRole(),
                null,
                null
        );

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRawRefreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpiryMs() / 1000)
                .build();
    }

    public void logout(String rawRefreshToken) {
        String tokenHash = hashToken(rawRefreshToken);
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            token.setRevoked(true);
            token.setRevokedAt(Instant.now());
            refreshTokenRepository.save(token);
        });
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