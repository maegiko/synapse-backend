package com.synapse.backend.notes.repositories;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.synapse.backend.notes.entities.Note;

public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Note> findByIdAndUserId(Long noteId, Long userId);

    Optional<Note> findByPublicIdAndUserId(UUID noteId, Long userId);

    long deleteByPublicIdAndUserId(UUID noteId, Long userId);

}
