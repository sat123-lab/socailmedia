package com.example.demo.repository;

import com.example.demo.entity.Reel;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ReelRepository extends JpaRepository<Reel, Long> {

    /**
     * Trending reels query. Pull a window of recent reels and rank by
     * {@code trendingScore} (precomputed).
     * PostgreSQL-safe: no nullable parameters in query.
     */
    @Query("""
           SELECT r FROM Reel r
           WHERE r.deleted = false
             AND r.visibility = 'PUBLIC'
           ORDER BY r.trendingScore DESC, r.createdAt DESC
           """)
    List<Reel> findTrending(Pageable pageable);

    /**
     * Trending reels query with cursor pagination.
     * PostgreSQL-safe: explicit non-null cursor parameter.
     */
    @Query("""
           SELECT r FROM Reel r
           WHERE r.deleted = false
             AND r.visibility = 'PUBLIC'
             AND r.createdAt < :cursorTs
           ORDER BY r.trendingScore DESC, r.createdAt DESC
           """)
    List<Reel> findTrendingBefore(@Param("cursorTs") LocalDateTime cursorTs, Pageable pageable);

    /**
     * Chronological feed (Following/All) used by the simple For-You fallback.
     * PostgreSQL-safe: no nullable parameters in query.
     */
    @Query("""
           SELECT r FROM Reel r
           WHERE r.deleted = false
             AND r.visibility = 'PUBLIC'
           ORDER BY r.createdAt DESC
           """)
    List<Reel> findRecent(Pageable pageable);

    /**
     * Chronological feed with cursor pagination.
     * PostgreSQL-safe: explicit non-null cursor parameter.
     */
    @Query("""
           SELECT r FROM Reel r
           WHERE r.deleted = false
             AND r.visibility = 'PUBLIC'
             AND r.createdAt < :cursorTs
           ORDER BY r.createdAt DESC
           """)
    List<Reel> findRecentBefore(@Param("cursorTs") LocalDateTime cursorTs, Pageable pageable);

    /**
     * Delegate method that routes to the appropriate query based on cursor presence.
     * PostgreSQL-safe: handles null cursor in Java, not in SQL.
     */
    default List<Reel> findTrendingWithCursor(LocalDateTime cursorTs, Pageable pageable) {
        if (cursorTs == null) {
            return findTrending(pageable);
        }
        return findTrendingBefore(cursorTs, pageable);
    }

    /**
     * Delegate method that routes to the appropriate query based on cursor presence.
     * PostgreSQL-safe: handles null cursor in Java, not in SQL.
     */
    default List<Reel> findRecentWithCursor(LocalDateTime cursorTs, Pageable pageable) {
        if (cursorTs == null) {
            return findRecent(pageable);
        }
        return findRecentBefore(cursorTs, pageable);
    }

    /** Reels by a single creator (profile grid). */
    @Query("""
           SELECT r FROM Reel r
           WHERE r.userId = :userId AND r.deleted = false
           ORDER BY r.createdAt DESC
           """)
    List<Reel> findByUser(@Param("userId") Long userId, Pageable pageable);

    @Modifying @Transactional
    @Query("UPDATE Reel r SET r.likesCount = r.likesCount + :delta WHERE r.id = :id")
    int bumpLikes(@Param("id") Long id, @Param("delta") int delta);

    @Modifying @Transactional
    @Query("UPDATE Reel r SET r.commentsCount = r.commentsCount + :delta WHERE r.id = :id")
    int bumpComments(@Param("id") Long id, @Param("delta") int delta);

    @Modifying @Transactional
    @Query("UPDATE Reel r SET r.sharesCount = r.sharesCount + 1 WHERE r.id = :id")
    int bumpShares(@Param("id") Long id);

    @Modifying @Transactional
    @Query("""
           UPDATE Reel r
              SET r.viewsCount = r.viewsCount + 1,
                  r.watchTimeSeconds = r.watchTimeSeconds + :watchSeconds
            WHERE r.id = :id
           """)
    int bumpViews(@Param("id") Long id, @Param("watchSeconds") long watchSeconds);

    @Modifying @Transactional
    @Query("UPDATE Reel r SET r.trendingScore = :score WHERE r.id = :id")
    int setScore(@Param("id") Long id, @Param("score") double score);

    // ====================================================================
    //  DISCOVERY — trending music aggregation
    // ====================================================================

    /**
     * Group recently created reels by their attached audio track and
     * return the top tracks by (uses × trending score). Used by the
     * Explore Hub's "Trending Music" rail when no dedicated music
     * catalog is wired in.
     *
     * The projection is {@code Object[]} of {@code [audioUrl, audioTitle,
     * count, sumTrendingScore, maxThumbnailUrl]} — the controller turns
     * it into a typed DTO. We restrict to public, non-deleted reels with
     * a non-blank audio URL so the rail only surfaces tracks that
     * actually exist.
     */
    @Query("""
           SELECT r.audioUrl,
                  MAX(r.audioTitle),
                  COUNT(r),
                  SUM(r.trendingScore),
                  MAX(r.thumbnailUrl)
             FROM Reel r
            WHERE r.deleted = false
              AND r.visibility = 'PUBLIC'
              AND r.audioUrl IS NOT NULL
              AND r.audioUrl <> ''
              AND r.createdAt >= :since
            GROUP BY r.audioUrl
            ORDER BY COUNT(r) DESC, SUM(r.trendingScore) DESC
           """)
    List<Object[]> findTrendingMusic(@Param("since") LocalDateTime since,
                                      Pageable pageable);
}
