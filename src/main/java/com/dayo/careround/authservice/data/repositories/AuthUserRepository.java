package com.dayo.careround.authservice.data.repositories;

import com.dayo.careround.authservice.data.models.AuthUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {
    Optional<AuthUser> findByEmailOrPhoneNumber(String email, String phoneNumber);
    Optional<AuthUser> findByPublicId(UUID publicId);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
}