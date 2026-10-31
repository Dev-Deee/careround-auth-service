package com.dayo.careround.authservice.dto.response;

import com.dayo.careround.authservice.data.models.enums.UserRole;

import java.util.UUID;

public record WardAssignmentResponse(
        UUID userId,
        UserRole role,
        String email,
        UUID previousWardId,
        UUID assignedWardId,
        String note
) {
    public static final String TOKEN_NOTE =
            "Access tokens issued before this change keep the old assignedWardId until they expire (at most 15 minutes); the next login or refresh carries the new ward.";
}
