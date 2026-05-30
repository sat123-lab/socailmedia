package com.example.demo.repository;

import com.example.demo.entity.ChatRoom;
import com.example.demo.entity.RoomCallSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomCallSessionRepository extends JpaRepository<RoomCallSession, Long> {

    List<RoomCallSession> findByStatus(String status);

    Optional<RoomCallSession> findFirstByRoomAndStatusInOrderByCreatedAtDesc(
            ChatRoom room,
            List<String> statuses
    );
}
