package com.example.demo.repository;

import com.example.demo.entity.ChatRoom;
import com.example.demo.entity.ChatRoomMember;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    @Query("""
            SELECT m FROM ChatRoomMember m
            JOIN FETCH m.room r
            WHERE m.user.id = :userId
            """)
    List<ChatRoomMember> findByUserIdWithRoom(@Param("userId") Long userId);

    List<ChatRoomMember> findByRoom(ChatRoom room);

    Optional<ChatRoomMember> findByRoomAndUser(ChatRoom room, User user);

    boolean existsByRoomAndUser(ChatRoom room, User user);
}
