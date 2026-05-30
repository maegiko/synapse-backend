package com.synapse.backend.quiz.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.synapse.backend.quiz.entities.Quiz;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    Optional<Quiz> findByPublicIdAndUserId(String publicId, Long userId);

    List<Quiz> findByUserIdOrderByCreatedAtDesc(Long userId);

    long deleteByPublicIdAndUserId(String publicId, Long userId);

}
