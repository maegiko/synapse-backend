package com.synapse.backend.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    @Modifying(flushAutomatically = true)
    @Query("""
        UPDATE User u
        SET u.totalFlashcardsReviewed = u.totalFlashcardsReviewed + :cardsReviewed
        WHERE u.id = :userId
    """)
    long incrementTotalFlashcardsReviewed(@Param("userId") Long userId, @Param("cardsReviewed") int cardsReviewed);

    @Query("SELECT u.totalFlashcardsReviewed FROM User u WHERE u.id = :userId")
    long findTotalFlashcardsReviewedById(@Param("userId") Long userId);

}
