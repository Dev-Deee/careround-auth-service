package com.dayo.careround.authservice.utils;

import com.dayo.careround.authservice.data.models.AuthUser;
import com.dayo.careround.authservice.data.models.RefreshToken;
import com.dayo.careround.authservice.data.models.enums.UserRole;
import com.dayo.careround.authservice.data.models.enums.UserStatus;
import com.dayo.careround.authservice.dto.request.RegisterRequest;
import com.dayo.careround.authservice.dto.response.AuthResponse;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AuthMapper {

    private AuthMapper() {
        // static utility class, not meant to be instantiated
    }

    // ---------------------------------------------------------------
    // Entity <-> Token/Response DTOs
    // ---------------------------------------------------------------

    public static AuthResponse toAuthResponse(String accessToken, String refreshToken, long expiresInMs) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInMs);
    }

    public static RefreshToken toRefreshToken(String tokenHash, java.util.UUID userId, String userRole, long expiryMs) {
        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(tokenHash);
        refreshToken.setUserId(userId);
        refreshToken.setUserRole(userRole);
        refreshToken.setIssuedAt(Instant.now());
        refreshToken.setExpiresAt(Instant.now().plusMillis(expiryMs));
        refreshToken.setRevoked(false);
        return refreshToken;
    }

    // ---------------------------------------------------------------
    // RegisterRequest -> AuthUser
    // ---------------------------------------------------------------

    public static AuthUser toAuthUser(RegisterRequest request) {
        AuthUser user = new AuthUser();
        user.setEmail(request.email());
        user.setPhoneNumber(request.phoneNumber());
        user.setRole(request.role());
        user.setGrade(request.grade());
        user.setAssignedWardId(request.assignedWardId());
        user.setStatus(UserStatus.PENDING_VERIFICATION);
        user.setPasswordHash(null);
        user.setPermissions(request.role() == UserRole.ADMIN
                ? request.permissions()
                : Set.of());
        return user;
    }

    // ---------------------------------------------------------------
    // AuthUser + RegisterRequest -> Kafka event payloads
    // ---------------------------------------------------------------

    public static Map<String, Object> toUserRegisteredPayload(AuthUser user, RegisterRequest request, String activationToken) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getId().toString());
        payload.put("publicId", user.getPublicId().toString());
        payload.put("email", user.getEmail());
        payload.put("role", user.getRole().name());
        payload.put("firstName", request.firstName());
        payload.put("lastName", request.lastName());
        payload.put("grade", request.grade());
        payload.put("specialization", request.specialization());
        payload.put("licenseNumber", request.licenseNumber());
        payload.put("activationToken", activationToken);
        return payload;
    }

    public static Map<String, Object> toVerificationResentPayload(AuthUser user, String activationToken) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", user.getId().toString());
        payload.put("email", user.getEmail());
        payload.put("activationToken", activationToken);
        return payload;
    }
}