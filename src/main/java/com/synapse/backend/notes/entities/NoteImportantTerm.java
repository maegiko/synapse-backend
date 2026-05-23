package com.synapse.backend.notes.entities;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "note_important_term")
public class NoteImportantTerm {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "note_id", nullable = false)
    private Long noteId;

    @Column(nullable = false)
    private Integer position;

    @Column(nullable = false)
    private String term;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false)
    private LocalDateTime updatedAt;

    protected NoteImportantTerm() {}

    public NoteImportantTerm(Long noteId, Integer position, String term) {
        this.noteId = noteId;
        this.position = position;
        this.term = term;
    }

    public String getTerm() {
        return term;
    }

}
