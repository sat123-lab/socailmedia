package com.example.demo.repository;

import com.example.demo.entity.RoomCallParticipant;
import com.example.demo.entity.RoomCallSession;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RoomCallParticipantRepository extends JpaRepository<RoomCallParticipant, Long> {

    Optional<RoomCallParticipant> findBySessionAndUser(RoomCallSession session, User user);

    List<RoomCallParticipant> findBySession(RoomCallSession session);

    @Query("""
            SELECT p FROM RoomCallParticipant p
            JOIN p.session s
            WHERE p.user = :user
              AND p.status = 'PENDING'
              AND s.status IN ('RINGING', 'ACTIVE')
            """)
    List<RoomCallParticipant> findPendingIncomingForUser(@Param("user") User user);

    long countBySessionAndStatus(RoomCallSession session, String status);
}
