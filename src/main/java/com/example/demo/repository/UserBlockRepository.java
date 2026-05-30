package com.example.demo.repository;

import com.example.demo.entity.User;
import com.example.demo.entity.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserBlockRepository extends JpaRepository<UserBlock, Long> {

    boolean existsByBlockerAndBlocked(User blocker, User blocked);

    Optional<UserBlock> findByBlockerAndBlocked(User blocker, User blocked);

    List<UserBlock> findByBlockerOrderByCreatedAtDesc(User blocker);

    void deleteByBlockerAndBlocked(User blocker, User blocked);
}
