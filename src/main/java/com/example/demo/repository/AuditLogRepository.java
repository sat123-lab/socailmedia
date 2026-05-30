package com.example.demo.repository;

import com.example.demo.entity.AuditLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

public interface AuditLogRepository extends JpaRepository<AuditLogEntry, Long> {

    Page<AuditLogEntry> findByActorUserIdOrderByCreatedAtDesc(Long actorUserId, Pageable page);

    Page<AuditLogEntry> findByActionOrderByCreatedAtDesc(String action, Pageable page);

    @Modifying
    @Transactional
    @Query("DELETE FROM AuditLogEntry a WHERE a.createdAt < :cutoff")
    int purgeOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
