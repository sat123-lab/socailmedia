package com.example.demo.repository;

import com.example.demo.entity.FeedSignal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface FeedSignalRepository extends JpaRepository<FeedSignal, Long> {

    /** Pulls every signal observed since [since] — the per-tick window for the
     *  roll-up. Ordered by id so retries are idempotent. */
    @Query("""
           SELECT s FROM FeedSignal s
            WHERE s.createdAt >= :since
            ORDER BY s.id ASC
           """)
    List<FeedSignal> findSinceOrderById(@Param("since") Instant since);

    /** Time-windowed signals targeting a specific (type, id). Used by the
     *  trending recompute path. */
    @Query("""
           SELECT s FROM FeedSignal s
            WHERE s.targetType = :type AND s.createdAt >= :since
            ORDER BY s.createdAt DESC
           """)
    List<FeedSignal> recentByType(
            @Param("type") String type, @Param("since") Instant since);

    long countByUserIdAndCreatedAtAfter(Long userId, Instant since);
}
