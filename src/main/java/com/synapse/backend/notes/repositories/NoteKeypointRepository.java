package com.synapse.backend.notes.repositories;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.synapse.backend.notes.entities.NoteKeypoint;

public interface NoteKeypointRepository extends JpaRepository<NoteKeypoint, Long> {

    List<NoteKeypoint> findByNoteIdOrderByPositionAsc(Long noteId);

}
