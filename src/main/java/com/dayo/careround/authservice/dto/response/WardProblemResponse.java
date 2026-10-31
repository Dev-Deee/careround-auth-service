package com.dayo.careround.authservice.dto.response;

import com.dayo.careround.authservice.data.models.enums.UserRole;

import java.util.UUID;

// problem: MISSING (no assignedWardId) or UNKNOWN_WARD (ward-service does not know the id).
public record WardProblemResponse(
        UUID userId,
        UserRole role,
        String email,
        UUID currentWardId,
        String problem
) {}
