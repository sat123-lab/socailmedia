package com.example.demo.repository;

import com.example.demo.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    Optional<FcmToken> findByToken(String token);

    List<FcmToken> findByUserIdAndInvalidFalse(Long userId);

    List<FcmToken> findByUserIdInAndInvalidFalse(Collection<Long> userIds);

    @Modifying @Transactional
    @Query("UPDATE FcmToken t SET t.invalid = true WHERE t.token = :token")
    int markInvalid(@Param("token") String token);
}
