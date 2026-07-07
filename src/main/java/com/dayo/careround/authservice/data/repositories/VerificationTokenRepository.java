package com.dayo.careround.authservice.data.repositories;

import com.dayo.careround.authservice.data.models.VerificationToken;
import com.dayo.careround.authservice.data.models.enums.VerificationTokenPurpose;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenRepository extends JpaRepository<VerificationToken, UUID> {
    Optional<VerificationToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("UPDATE VerificationToken v SET v.used = true WHERE v.userId = :userId AND v.used = false")
    void invalidateAllForUser(UUID userId);

    Optional<VerificationToken> findByTokenHashAndPurpose(String tokenHash, VerificationTokenPurpose purpose);

    @Modifying
    @Query("UPDATE VerificationToken v SET v.used = true WHERE v.userId = :userId AND v.purpose = :purpose AND v.used = false")
    void invalidateAllForUserAndPurpose(UUID userId, VerificationTokenPurpose purpose);
}