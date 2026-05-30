package com.example.demo.repository;

import com.example.demo.entity.GroupCallJoinRequest;
import com.example.demo.entity.GroupCallSession;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupCallJoinRequestRepository extends JpaRepository<GroupCallJoinRequest, Long> {

    Optional<GroupCallJoinRequest> findBySessionAndUserAndStatus(
            GroupCallSession session,
            User user,
            String status
    );

    List<GroupCallJoinRequest> findBySessionAndStatusOrderByCreatedAtAsc(
            GroupCallSession session,
            String status
    );

    long countBySessionAndStatus(GroupCallSession session, String status);
}
