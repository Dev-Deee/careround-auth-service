package com.dayo.careround.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RefreshRequest {

    @NotBlank
    private String refreshToken;
}