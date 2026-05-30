package com.example.demo.repository;

import com.example.demo.entity.GroupCallSession;
import com.example.demo.entity.SocialGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GroupCallSessionRepository extends JpaRepository<GroupCallSession, Long> {

    List<GroupCallSession> findByStatus(String status);

    Optional<GroupCallSession> findFirstByGroupAndStatusInOrderByCreatedAtDesc(
            SocialGroup group,
            List<String> statuses
    );
}
