package com.dayo.careround.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ConfirmEmailChangeRequest(
        @NotBlank String token
) {}
