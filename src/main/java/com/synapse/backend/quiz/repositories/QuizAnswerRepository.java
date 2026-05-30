package com.synapse.backend.quiz.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.synapse.backend.quiz.entities.QuizAnswer;

public interface QuizAnswerRepository extends JpaRepository<QuizAnswer, Long> {

    List<QuizAnswer> findByQuestionIdOrderByPositionAsc(Long questionId);

    List<QuizAnswer> findByQuestionIdInOrderByQuestionIdAscPositionAsc(List<Long> questionIds);

}
