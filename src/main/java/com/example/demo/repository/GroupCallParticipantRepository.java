package com.example.demo.repository;

import com.example.demo.entity.GroupCallParticipant;
import com.example.demo.entity.GroupCallSession;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GroupCallParticipantRepository extends JpaRepository<GroupCallParticipant, Long> {

    Optional<GroupCallParticipant> findBySessionAndUser(GroupCallSession session, User user);

    List<GroupCallParticipant> findBySession(GroupCallSession session);

    @Query("""
            SELECT p FROM GroupCallParticipant p
            JOIN p.session s
            WHERE p.user = :user
              AND p.status = 'PENDING'
              AND s.status IN ('RINGING', 'ACTIVE')
            """)
    List<GroupCallParticipant> findPendingIncomingForUser(@Param("user") User user);

    long countBySessionAndStatus(GroupCallSession session, String status);
}
