package com.dayo.careround.authservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank
    private String identifier; // email or phone number

    @NotBlank
    private String password;
}