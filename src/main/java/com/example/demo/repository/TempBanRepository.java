package com.example.demo.repository;

import com.example.demo.entity.TempBan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TempBanRepository extends JpaRepository<TempBan, Long> {

    Optional<TempBan> findFirstBySubjectAndExpiresAtAfterOrderByExpiresAtDesc(
            String subject, LocalDateTime now);

    List<TempBan> findByExpiresAtAfterOrderByIssuedAtDesc(LocalDateTime now);

    @Modifying
    @Transactional
    @Query("DELETE FROM TempBan b WHERE b.expiresAt < :now")
    int purgeExpired(@Param("now") LocalDateTime now);
}
