package com.synapse.backend.auth.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.synapse.backend.auth.entities.RefreshToken;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE RefreshToken r
        SET r.revokedAt = CURRENT_TIMESTAMP
        WHERE r.tokenHash = :tokenHash
            AND r.revokedAt IS NULL
            AND r.expiresAt > CURRENT_TIMESTAMP
    """)
    long revokeActiveByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE RefreshToken r
        SET r.revokedAt = CURRENT_TIMESTAMP
        WHERE r.userId = :userId
            AND r.revokedAt IS NULL
    """)
    long revokeActiveByUserId(@Param("userId") Long userId);

}
