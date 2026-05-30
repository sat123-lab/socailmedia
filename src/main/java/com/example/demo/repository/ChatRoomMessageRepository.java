package com.example.demo.repository;

import com.example.demo.entity.ChatRoom;
import com.example.demo.entity.ChatRoomMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatRoomMessageRepository extends JpaRepository<ChatRoomMessage, Long> {

    List<ChatRoomMessage> findByRoomOrderByCreatedAtAsc(ChatRoom room);

    @Query("""
           SELECT m FROM ChatRoomMessage m
           WHERE (:senderId IS NULL OR m.sender.id = :senderId)
             AND (:from IS NULL OR m.createdAt >= :from)
             AND (:to   IS NULL OR m.createdAt <= :to)
             AND (:encryptedOnly = false OR m.encrypted = true)
           ORDER BY m.createdAt DESC
           """)
    Page<ChatRoomMessage> adminSearch(
            @Param("senderId") Long senderId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("encryptedOnly") boolean encryptedOnly,
            Pageable pageable);
}
