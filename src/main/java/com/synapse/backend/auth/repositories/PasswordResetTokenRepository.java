package com.synapse.backend.auth.repositories;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.synapse.backend.auth.entities.PasswordResetToken;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE PasswordResetToken t
        SET t.consumedAt = CURRENT_TIMESTAMP
        WHERE t.tokenHash = :tokenHash
            AND t.consumedAt IS NULL
            AND t.invalidatedAt IS NULL
            AND t.expiresAt > CURRENT_TIMESTAMP
    """)
    long consumeActiveByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE PasswordResetToken t
        SET t.invalidatedAt = CURRENT_TIMESTAMP
        WHERE t.userId = :userId
            AND t.consumedAt IS NULL
            AND t.invalidatedAt IS NULL
    """)
    long invalidateActiveByUserId(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Query("""
        DELETE FROM PasswordResetToken t
        WHERE t.expiresAt < :cutoff
    """)
    long deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);

}
