package com.example.demo.repository;

import com.example.demo.entity.SocialGroup;
import com.example.demo.entity.SocialGroupMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface SocialGroupMessageRepository extends JpaRepository<SocialGroupMessage, Long> {

    List<SocialGroupMessage> findByGroupOrderByCreatedAtAsc(SocialGroup group);

    @Query("""
           SELECT m FROM SocialGroupMessage m
           WHERE (:senderId IS NULL OR m.sender.id = :senderId)
             AND (:from IS NULL OR m.createdAt >= :from)
             AND (:to   IS NULL OR m.createdAt <= :to)
             AND (:encryptedOnly = false OR m.encrypted = true)
           ORDER BY m.createdAt DESC
           """)
    Page<SocialGroupMessage> adminSearch(
            @Param("senderId") Long senderId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("encryptedOnly") boolean encryptedOnly,
            Pageable pageable);
}
