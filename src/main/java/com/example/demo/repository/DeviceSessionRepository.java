package com.example.demo.repository;

import com.example.demo.entity.DeviceSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface DeviceSessionRepository extends JpaRepository<DeviceSession, Long> {

    List<DeviceSession> findByUserIdAndRevokedFalseOrderByLastSeenAtDesc(Long userId);

    Optional<DeviceSession> findByRefreshFamilyId(String familyId);

    @Modifying
    @Transactional
    @Query("UPDATE DeviceSession s SET s.revoked = true, s.revokedAt = :now " +
            "WHERE s.userId = :userId AND s.revoked = false")
    int revokeAllForUser(@Param("userId") Long userId, @Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("UPDATE DeviceSession s SET s.lastSeenAt = :now " +
            "WHERE s.refreshFamilyId = :familyId")
    int touch(@Param("familyId") String familyId, @Param("now") LocalDateTime now);
}
