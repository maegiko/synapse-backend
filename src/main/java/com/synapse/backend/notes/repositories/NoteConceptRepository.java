package com.synapse.backend.notes.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.synapse.backend.notes.entities.NoteConcept;

public interface NoteConceptRepository extends JpaRepository<NoteConcept, Long> {

    List<NoteConcept> findByNoteIdOrderByPositionAsc(Long noteId);

}
