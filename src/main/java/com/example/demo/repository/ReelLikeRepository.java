package com.example.demo.repository;

import com.example.demo.entity.ReelLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReelLikeRepository extends JpaRepository<ReelLike, Long> {
    Optional<ReelLike> findByReelIdAndUserId(Long reelId, Long userId);

    @Modifying @Transactional
    @Query("DELETE FROM ReelLike rl WHERE rl.reelId = :rid AND rl.userId = :uid")
    int deleteByReelAndUser(@Param("rid") Long rid, @Param("uid") Long uid);

    @Query("SELECT rl.reelId FROM ReelLike rl WHERE rl.userId = :uid AND rl.reelId IN :ids")
    List<Long> findLikedIds(@Param("uid") Long uid, @Param("ids") Collection<Long> ids);
}
