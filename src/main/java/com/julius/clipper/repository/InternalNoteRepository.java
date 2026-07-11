package com.julius.clipper.repository;

import com.julius.clipper.domain.InternalNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface InternalNoteRepository extends JpaRepository<InternalNote, String> {
    List<InternalNote> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, String entityId);
}
