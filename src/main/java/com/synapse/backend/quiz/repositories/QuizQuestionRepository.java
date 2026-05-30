package com.synapse.backend.quiz.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.synapse.backend.quiz.entities.QuizQuestion;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    List<QuizQuestion> findByQuizIdOrderByPositionAsc(Long quizId);

    List<QuizQuestion> findByQuizIdInOrderByQuizIdAscPositionAsc(List<Long> quizIds);

}
