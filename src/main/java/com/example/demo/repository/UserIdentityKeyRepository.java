package com.example.demo.repository;

import com.example.demo.entity.UserIdentityKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserIdentityKeyRepository extends JpaRepository<UserIdentityKey, Long> {
    Optional<UserIdentityKey> findByUserId(Long userId);
}
