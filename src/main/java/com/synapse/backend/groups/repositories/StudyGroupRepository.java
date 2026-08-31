package com.synapse.backend.groups.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.synapse.backend.groups.entities.StudyGroup;

public interface StudyGroupRepository extends JpaRepository<StudyGroup, Long> {

    List<StudyGroup> findByUserIdOrderByCreatedAtDesc(Long userId);

    Page<StudyGroup> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Pageable pageable);

    Page<StudyGroup> findByUserIdAndNameContainingIgnoreCaseOrderByCreatedAtDescIdDesc(
        Long userId,
        String name,
        Pageable pageable
    );

    Optional<StudyGroup> findByPublicIdAndUserId(String publicId, Long userId);

    long deleteByPublicIdAndUserId(String publicId, Long userId);

    @Query("SELECT g.publicId FROM StudyGroup g WHERE g.id = :groupId")
    Optional<String> findPublicIdById(@Param("groupId") Long groupId);

}
