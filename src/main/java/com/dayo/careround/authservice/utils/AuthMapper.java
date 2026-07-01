package com.dayo.careround.authservice.utils;

import com.dayo.careround.authservice.data.models.RefreshToken;
import com.dayo.careround.authservice.dto.response.AuthResponse;

import java.time.Instant;
import java.util.UUID;

public class AuthMapper {

    private AuthMapper() {}

    public static AuthResponse toAuthResponse(
            String accessToken,
            String rawRefreshToken,
            long accessTokenExpiryMs) {
        return new AuthResponse(
                accessToken,
                rawRefreshToken,
                "Bearer",
                accessTokenExpiryMs / 1000
        );
    }

    public static RefreshToken toRefreshToken(
            String tokenHash,
            UUID userId,
            String userRole,
            long refreshTokenExpiryMs) {
        RefreshToken token = new RefreshToken();
        token.setTokenHash(tokenHash);
        token.setUserId(userId);
        token.setUserRole(userRole);
        token.setIssuedAt(Instant.now());
        token.setExpiresAt(Instant.now().plusMillis(refreshTokenExpiryMs));
        return token;
    }
}