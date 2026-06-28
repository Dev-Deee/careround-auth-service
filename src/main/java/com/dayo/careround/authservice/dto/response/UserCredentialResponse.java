package com.dayo.careround.authservice.dto.response;

import lombok.Data;
import java.util.UUID;

@Data
public class UserCredentialResponse {
    private UUID id;
    private UUID publicId;
    private String email;
    private String phoneNumber;
    private String passwordHash;
    private String role;
    private String grade;
    private UUID assignedWardId;
    private String status;
}