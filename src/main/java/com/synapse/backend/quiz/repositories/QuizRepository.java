package com.synapse.backend.quiz.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.synapse.backend.quiz.entities.Quiz;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    Optional<Quiz> findByPublicIdAndUserId(String publicId, Long userId);

    List<Quiz> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<Quiz> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Page<Quiz> findByUserIdAndTitleContainingIgnoreCaseOrderByCreatedAtDescIdDesc(
        Long userId,
        String title,
        Pageable pageable
    );

    long deleteByPublicIdAndUserId(String publicId, Long userId);

    List<Quiz> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    int countByGroupId(Long groupId);

    @Modifying
    @Query("""
        UPDATE Quiz q
        SET q.groupId = :groupId
        WHERE q.publicId = :publicId AND q.userId = :userId
    """)
    long updateGroupIdByPublicIdAndUserId(
        @Param("publicId") String publicId,
        @Param("userId") Long userId,
        @Param("groupId") Long groupId
    );

    @Modifying
    @Query("""
        UPDATE Quiz q
        SET q.groupId = NULL
        WHERE q.publicId = :publicId AND q.userId = :userId AND q.groupId = :groupId
    """)
    long clearGroupIdByPublicIdAndUserId(
        @Param("publicId") String publicId,
        @Param("userId") Long userId,
        @Param("groupId") Long groupId
    );

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
