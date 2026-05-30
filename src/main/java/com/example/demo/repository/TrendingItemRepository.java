package com.example.demo.repository;

import com.example.demo.entity.TrendingItem;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TrendingItemRepository extends JpaRepository<TrendingItem, Long> {

    Optional<TrendingItem> findByTargetTypeAndTargetIdAndTargetLabel(
            String targetType, Long targetId, String targetLabel);

    List<TrendingItem> findByTargetTypeOrderByScoreDesc(
            String targetType, Pageable page);

    List<TrendingItem> findByTargetTypeAndCategoryOrderByScoreDesc(
            String targetType, String category, Pageable page);

    /**
     * Multiplies all scores by [decay] — a simple drum-beat exponential
     * decay between roll-up batches that prevents the table from being
     * dominated by yesterday's viral content.
     */
    @Modifying
    @Transactional
    @Query("UPDATE TrendingItem t SET t.score = t.score * :decay, t.updatedAt = :now")
    void decayAll(@Param("decay") double decay, @Param("now") Instant now);

    /** Drops items that have decayed below the relevance floor. */
    @Modifying
    @Transactional
    @Query("DELETE FROM TrendingItem t WHERE t.score < :floor")
    int purgeBelow(@Param("floor") double floor);
}
