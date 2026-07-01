package com.dayo.careround.authservice.dto.request;

import com.dayo.careround.authservice.data.models.enums.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record RegisterRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank @Email String email,
        @NotBlank String phoneNumber,
        @NotBlank String password,
        @NotNull UserRole role,
        String grade,
        UUID assignedWardId,
        String specialization,
        String licenseNumber
) {}