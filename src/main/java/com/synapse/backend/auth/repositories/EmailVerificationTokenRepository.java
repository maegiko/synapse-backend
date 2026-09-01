package com.synapse.backend.auth.repositories;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.synapse.backend.auth.entities.EmailVerificationToken;
import com.synapse.backend.auth.enums.EmailVerificationPurpose;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    Optional<EmailVerificationToken> findByTokenHash(String tokenHash);

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE EmailVerificationToken t
        SET t.consumedAt = CURRENT_TIMESTAMP
        WHERE t.tokenHash = :tokenHash
            AND t.consumedAt IS NULL
            AND t.invalidatedAt IS NULL
            AND t.expiresAt > CURRENT_TIMESTAMP
    """)
    long consumeActiveByTokenHash(@Param("tokenHash") String tokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE EmailVerificationToken t
        SET t.invalidatedAt = CURRENT_TIMESTAMP
        WHERE t.userId = :userId
            AND t.purpose = :purpose
            AND t.consumedAt IS NULL
            AND t.invalidatedAt IS NULL
    """)
    long invalidateActiveByUserIdAndPurpose(
        @Param("userId") Long userId,
        @Param("purpose") EmailVerificationPurpose purpose
    );

    @Modifying(clearAutomatically = true)
    @Query("""
        DELETE FROM EmailVerificationToken t
        WHERE t.expiresAt < :cutoff
    """)
    long deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);

}
