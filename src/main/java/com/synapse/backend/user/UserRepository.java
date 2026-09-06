package com.synapse.backend.user;

import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleSubject(String googleSubject);

    /**
     * Attaches a Google subject to an account that has none, in one statement.
     *
     * <p>Read-then-write cannot do this safely. Two credentials carrying different
     * subjects for one authoritative address both see a null {@code google_subject}
     * and both write, and because the two values differ the unique index never
     * fires: the account silently ends up linked to whichever wrote last, and both
     * callers get a session. Checking and writing in a single conditional update
     * lets exactly one of them win, and the loser sees zero rows and is refused.
     * Do not replace this with a read followed by a save.</p>
     *
     * @param userId the account to link.
     * @param googleSubject Google's stable subject claim.
     * @return 1 when the account was linked, 0 when it already had a subject.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE User u
        SET u.googleSubject = :googleSubject
        WHERE u.id = :userId
            AND u.googleSubject IS NULL
    """)
    long linkGoogleSubjectIfAbsent(
        @Param("userId") Long userId,
        @Param("googleSubject") String googleSubject
    );

    /**
     * Attaches a Google subject, but only while the account still has the password
     * the caller was checked against.
     *
     * <p>Explicit linking proves a password and then writes, and those are two
     * statements. A password reset landing between them would otherwise be
     * undone: somebody holding the old password and a still-valid access token
     * could attach their own Google Account moments after the owner recovered the
     * account, leaving a way in the reset was meant to close. Carrying the
     * observed hash into the update closes that window, because the reset changes
     * the hash and the update then matches nothing.</p>
     *
     * @param userId the account to link.
     * @param googleSubject Google's stable subject claim.
     * @param expectedPasswordHash the hash the supplied password was verified against.
     * @return 1 when the account was linked, 0 when it already had a subject or its password has since changed.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE User u
        SET u.googleSubject = :googleSubject
        WHERE u.id = :userId
            AND u.googleSubject IS NULL
            AND u.passwordHash = :expectedPasswordHash
    """)
    long linkGoogleSubjectIfPasswordUnchanged(
        @Param("userId") Long userId,
        @Param("googleSubject") String googleSubject,
        @Param("expectedPasswordHash") String expectedPasswordHash
    );

    /**
     * Removes a Google subject, and reports whether there was one to remove.
     *
     * <p>Unlinking ends every session, so whether anything was actually unlinked
     * decides whether the caller is signed out. Asking the update itself keeps
     * that answer honest when two unlinks race.</p>
     *
     * @param userId the account to unlink.
     * @return 1 when a subject was removed, 0 when the account had none.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE User u
        SET u.googleSubject = NULL
        WHERE u.id = :userId
            AND u.googleSubject IS NOT NULL
    """)
    long unlinkGoogleSubjectIfPresent(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE User u
        SET u.totalFlashcardsReviewed = u.totalFlashcardsReviewed + :cardsReviewed
        WHERE u.id = :userId
    """)
    long incrementTotalFlashcardsReviewed(@Param("userId") Long userId, @Param("cardsReviewed") int cardsReviewed);

    @Query("SELECT u.totalFlashcardsReviewed FROM User u WHERE u.id = :userId")
    long findTotalFlashcardsReviewedById(@Param("userId") Long userId);

    @Query("SELECT u.timeZone FROM User u WHERE u.id = :userId")
    Optional<String> findTimeZoneById(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true)
    @Query("""
        DELETE FROM User u
        WHERE u.emailVerifiedAt IS NULL
            AND u.createdAt < :cutoff
    """)
    long deleteUnverifiedCreatedBefore(@Param("cutoff") LocalDateTime cutoff);

}
