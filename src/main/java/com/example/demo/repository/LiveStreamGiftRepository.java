package com.example.demo.repository;

import com.example.demo.entity.LiveStreamGift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LiveStreamGiftRepository extends JpaRepository<LiveStreamGift, Long> {
    @Query("SELECT COALESCE(SUM(g.giftValue), 0) FROM LiveStreamGift g WHERE g.stream.id = :streamId")
    long sumValueByStream(@Param("streamId") Long streamId);

    @Query("SELECT COALESCE(SUM(g.giftValue), 0) FROM LiveStreamGift g WHERE g.creator.id = :creatorId")
    long sumValueByCreator(@Param("creatorId") Long creatorId);
}
