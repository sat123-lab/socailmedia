package com.example.demo.repository;

import com.example.demo.entity.OneTimePreKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OneTimePreKeyRepository extends JpaRepository<OneTimePreKey, Long> {

    /** Hand the oldest unused pre-key to a sender. */
    @Query("SELECT k FROM OneTimePreKey k WHERE k.userId = :userId AND k.used = false " +
            "ORDER BY k.createdAt ASC")
    List<OneTimePreKey> findUnused(@Param("userId") Long userId);

    Optional<OneTimePreKey> findByUserIdAndKeyId(Long userId, String keyId);

    long countByUserIdAndUsed(Long userId, boolean used);
}
