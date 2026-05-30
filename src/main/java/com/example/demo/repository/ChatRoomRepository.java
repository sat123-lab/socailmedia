package com.example.demo.repository;

import com.example.demo.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    Optional<ChatRoom> findByInviteCode(String inviteCode);

    @Query("""
            SELECT DISTINCT r FROM ChatRoom r
            JOIN ChatRoomMember m ON m.room.id = r.id
            WHERE m.user.id = :userId
            ORDER BY r.createdAt DESC
            """)
    List<ChatRoom> findRoomsForUser(Long userId);
}
