package com.dayo.careround.authservice.data.models;

import com.dayo.careround.authservice.data.models.enums.UserRole;
import com.dayo.careround.authservice.data.models.enums.UserStatus;
import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "auth_users", indexes = {
        @Index(name = "idx_auth_users_email", columnList = "email"),
        @Index(name = "idx_auth_users_phone", columnList = "phone_number")
})
public class AuthUser {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, updatable = false)
    private UUID publicId = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String phoneNumber;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    @Column
    private String grade; // DoctorGrade or NurseGrade depending on role

    @Column
    private UUID assignedWardId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.ACTIVE;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
}