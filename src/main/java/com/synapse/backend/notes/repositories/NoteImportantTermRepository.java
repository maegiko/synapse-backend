package com.synapse.backend.notes.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.synapse.backend.notes.entities.NoteImportantTerm;

public interface NoteImportantTermRepository extends JpaRepository<NoteImportantTerm, Long> {

    List<NoteImportantTerm> findByNoteIdOrderByPositionAsc(Long noteId);

}
