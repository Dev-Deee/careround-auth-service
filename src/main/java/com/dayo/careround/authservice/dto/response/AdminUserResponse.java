package com.dayo.careround.authservice.dto.response;

import com.dayo.careround.authservice.data.models.enums.AdminPermission;
import com.dayo.careround.authservice.data.models.enums.UserRole;
import com.dayo.careround.authservice.data.models.enums.UserStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

// One account in the admin user list. id is the internal user id (JWT sub, the id other services store for staff);
// names live in user-service (join on id via its directory).
public record AdminUserResponse(
        UUID id,
        UUID publicId,
        String email,
        String phoneNumber,
        UserRole role,
        String grade,
        UserStatus status,
        UUID assignedWardId,
        Set<AdminPermission> permissions,
        Instant createdAt
) {}
