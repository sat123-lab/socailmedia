package com.example.demo.repository;

import com.example.demo.entity.RoomCallJoinRequest;
import com.example.demo.entity.RoomCallSession;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RoomCallJoinRequestRepository extends JpaRepository<RoomCallJoinRequest, Long> {

    Optional<RoomCallJoinRequest> findBySessionAndUserAndStatus(
            RoomCallSession session,
            User user,
            String status
    );

    List<RoomCallJoinRequest> findBySessionAndStatusOrderByCreatedAtAsc(
            RoomCallSession session,
            String status
    );

    long countBySessionAndStatus(RoomCallSession session, String status);
}
