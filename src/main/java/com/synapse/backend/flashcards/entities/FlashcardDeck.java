package com.synapse.backend.flashcards.entities;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "flashcard_deck")
public class FlashcardDeck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "note_id", nullable = true)
    private Long noteId;

    @Column(nullable = false)
    private String title;

    @Column(name = "source_type", nullable = false)
    private String sourceType;

    @Column(name = "public_id", nullable = false, unique = true)
    private UUID publicId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected FlashcardDeck() {}

    public FlashcardDeck(Long userId, Long noteId, String title, String sourceType, UUID publicId) {
        this.userId = userId;
        this.noteId = noteId;
        this.title = title;
        this.sourceType = sourceType;
        this.publicId = publicId;
    }

    public Long getId() {
        return id;
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

    public String getSourceType() {
        return sourceType;
    }

    public UUID getPublicId() {
        return publicId;
    }

}
