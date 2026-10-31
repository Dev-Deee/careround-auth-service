package com.dayo.careround.authservice.dto.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

// wardId is the ward's publicId (the id ward-service exposes).
public record AssignWardRequest(
        @NotNull UUID wardId
) {}
