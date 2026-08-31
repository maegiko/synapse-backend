package com.synapse.backend.quiz.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.synapse.backend.quiz.entities.QuizQuestion;

import jakarta.persistence.LockModeType;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    List<QuizQuestion> findByQuizIdOrderByPositionAsc(Long quizId);

    List<QuizQuestion> findByQuizIdInOrderByQuizIdAscPositionAsc(List<Long> quizIds);

    @Query("SELECT MAX(q.position) FROM QuizQuestion q WHERE q.quizId =:quizId")
    Optional<Integer> findMaxPositionByQuizId(@Param("quizId") Long quizId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT q FROM QuizQuestion q
        WHERE q.publicId = :publicId AND q.quizId = :quizId
    """)
    Optional<QuizQuestion> findByPublicIdAndQuizIdForUpdate(
        @Param("publicId") String publicId,
        @Param("quizId") Long quizId
    );

    long deleteByPublicIdAndQuizId(String publicId, Long quizId);

    int countByQuizId(Long quizId);

}
