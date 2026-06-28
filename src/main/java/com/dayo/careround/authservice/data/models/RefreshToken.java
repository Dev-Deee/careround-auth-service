package com.dayo.careround.authservice.data.models;

import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String userRole;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Instant issuedAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column
    private Instant revokedAt;
}