package com.synapse.backend.quiz.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.synapse.backend.quiz.entities.QuizScore;

public interface QuizScoreRepository extends JpaRepository<QuizScore, Long> {

    Optional<QuizScore> findByPublicIdAndQuizId(String publicId, Long quizId);

    List<QuizScore> findByQuizIdOrderByCreatedAtDesc(Long quizId);

}
