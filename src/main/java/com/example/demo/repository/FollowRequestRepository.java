package com.example.demo.repository;

import com.example.demo.entity.FollowRequest;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface FollowRequestRepository
        extends JpaRepository<FollowRequest, Long> {

    Optional<FollowRequest> findByRequesterAndTarget(
            User requester,
            User target
    );

    Optional<FollowRequest> findByRequesterAndTargetAndStatus(
            User requester,
            User target,
            String status
    );

    boolean existsByRequesterAndTargetAndStatus(
            User requester,
            User target,
            String status
    );

    List<FollowRequest> findByTargetAndStatus(
            User target,
            String status
    );

    Optional<FollowRequest> findByIdAndTarget(
            Long id,
            User target
    );

    @Modifying
    @Transactional
    @Query("DELETE FROM FollowRequest fr WHERE fr.requester = :user OR fr.target = :user")
    void deleteAllForUser(@Param("user") User user);
}
