package com.synapse.backend.notes.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.synapse.backend.notes.entities.Note;

public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Note> findByIdAndUserId(Long noteId, Long userId);

    Optional<Note> findByPublicIdAndUserId(String noteId, Long userId);

    long deleteByPublicIdAndUserId(String noteId, Long userId);

    List<Note> findByGroupIdOrderByCreatedAtDesc(Long groupId);

    int countByGroupId(Long groupId);

    @Modifying
    @Query("""
        UPDATE Note n
        SET n.groupId = :groupId
        WHERE n.publicId = :publicId AND n.userId = :userId
    """)
    long updateGroupIdByPublicIdAndUserId(
        @Param("publicId") String publicId,
        @Param("userId") Long userId,
        @Param("groupId") Long groupId
    );

    @Modifying
    @Query("""
        UPDATE Note n
        SET n.groupId = NULL
        WHERE n.publicId = :publicId AND n.userId = :userId AND n.groupId = :groupId
    """)
    long clearGroupIdByPublicIdAndUserId(
        @Param("publicId") String publicId,
        @Param("userId") Long userId,
        @Param("groupId") Long groupId
    );

}
