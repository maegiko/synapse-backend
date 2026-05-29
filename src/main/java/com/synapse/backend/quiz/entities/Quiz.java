package com.synapse.backend.quiz.entities;

import java.time.LocalDateTime;

import com.aventrix.jnanoid.jnanoid.NanoIdUtils;
import com.synapse.backend.quiz.enums.QuizSourceType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "quiz")
public class Quiz {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 10)
    private String publicId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "note_id")
    private Long noteId;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Column(name = "source_type", nullable = false)
    @Enumerated(EnumType.STRING)
    private QuizSourceType sourceType;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private LocalDateTime updatedAt;

    protected Quiz() {}

    public Quiz(String title, String description, Long userId, Long noteId, QuizSourceType sourceType) {
        this.title = title;
        this.description = description;
        this.userId = userId;
        this.noteId = noteId;
        this.sourceType = sourceType;
        this.publicId = NanoIdUtils.randomNanoId(
            NanoIdUtils.DEFAULT_NUMBER_GENERATOR,
            NanoIdUtils.DEFAULT_ALPHABET,
            10
        );
    }

    public String getPublicId() {
        return publicId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getNoteId() {
        return noteId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public QuizSourceType getSourceType() {
        return sourceType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

}
