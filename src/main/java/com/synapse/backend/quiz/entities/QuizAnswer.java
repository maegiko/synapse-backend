package com.synapse.backend.quiz.entities;

import java.time.LocalDateTime;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz_answer")
public class QuizAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 10)
    private String publicId;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "answer_text", nullable = false)
    private String answerText;

    @Column(name = "is_correct", nullable = false)
    private boolean isCorrect;

    @Column(nullable = false)
    private Integer position;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private LocalDateTime updatedAt;

    protected QuizAnswer() {}

    public QuizAnswer(Long questionId, String answerText, boolean isCorrect, Integer position) {
        this.questionId = questionId;
        this.answerText = answerText;
        this.isCorrect = isCorrect;
        this.position = position;
        this.publicId = NanoIdUtils.randomNanoId(
            NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
            NanoIdUtils.DEFAULT_ALPHABET,
            10
        );
    }

    public String getPublicId() {
        return publicId;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public String getAnswerText() {
        return answerText;
    }

    public boolean isCorrect() {
        return isCorrect;
    }

    public Integer getPosition() {
        return position;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Long getId() {
        return id;
    }

}
