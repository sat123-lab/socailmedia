package com.example.demo.repository;

import com.example.demo.entity.CallSession;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CallSessionRepository extends JpaRepository<CallSession, Long> {

    @Query("""
            SELECT c FROM CallSession c
            WHERE (c.receiver = :user OR c.caller = :user)
              AND c.status IN ('RINGING', 'ACTIVE')
            """)
    List<CallSession> findPendingForUser(@Param("user") User user);

    List<CallSession> findByReceiverAndStatus(User receiver, String status);

    List<CallSession> findByCallerAndStatus(User caller, String status);

    List<CallSession> findByReceiverAndStatusIn(User receiver, List<String> statuses);

    List<CallSession> findByCallerAndStatusIn(User caller, List<String> statuses);

    boolean existsByCallerAndStatusIn(User caller, List<String> statuses);

    boolean existsByReceiverAndStatusIn(User receiver, List<String> statuses);

    List<CallSession> findByStatus(String status);

    /** Any non-terminal session between two users (blocks new calls). */
    @Query("""
            SELECT c FROM CallSession c
            WHERE ((c.caller = :u1 AND c.receiver = :u2)
                OR (c.caller = :u2 AND c.receiver = :u1))
              AND c.status IN ('RINGING', 'ACTIVE')
            """)
    List<CallSession> findOpenBetween(
            @Param("u1") User u1,
            @Param("u2") User u2);

    @Query("""
            SELECT c FROM CallSession c
            WHERE (c.caller = :user OR c.receiver = :user)
              AND c.status = 'ACTIVE'
            """)
    List<CallSession> findActiveForUser(@Param("user") User user);
}
