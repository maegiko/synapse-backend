package com.synapse.backend.quiz.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.synapse.backend.quiz.entities.Quiz;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    Optional<Quiz> findByPublicIdAndUserId(String publicId, Long userId);

    List<Quiz> findByUserIdOrderByCreatedAtDesc(Long userId);

    long deleteByPublicIdAndUserId(String publicId, Long userId);

    @Modifying
    @Query("""
        UPDATE Quiz q
        SET q.updatedAt = CURRENT_TIMESTAMP
        WHERE q.id = :quizId
    """)
    long updateUpdatedAtById(@Param("quizId") Long quizId);

    @Modifying
    @Query("""
        UPDATE Quiz q
        SET q.difficulty = :difficulty,
            q.updatedAt = CURRENT_TIMESTAMP
        WHERE q.id = :quizId
    """)
    long updateDifficultyById(@Param("quizId") Long quizId, @Param("difficulty") Integer difficulty);

}
