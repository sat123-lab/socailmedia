package com.example.demo.repository;

import com.example.demo.entity.LiveStream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LiveStreamRepository extends JpaRepository<LiveStream, Long> {

    /** Most engaging streams happening right now — ordered by current viewer count desc. */
    @Query("""
           SELECT s FROM LiveStream s
            WHERE s.state = 'LIVE'
              AND (:category IS NULL OR s.category = :category)
            ORDER BY s.peakViewers DESC, s.startedAt DESC
           """)
    Page<LiveStream> trending(@Param("category") String category, Pageable pageable);

    /** Live streams from creators followed by a given user. */
    @Query("""
           SELECT s FROM LiveStream s
            WHERE s.state = 'LIVE'
              AND s.creator.id IN (
                SELECT f.following.id FROM Follow f WHERE f.follower.id = :userId
              )
            ORDER BY s.startedAt DESC
           """)
    List<LiveStream> followedLive(@Param("userId") Long userId);

    /** Most recent currently-live session for a given creator, if any. */
    Optional<LiveStream> findFirstByCreator_IdAndStateOrderByStartedAtDesc(Long creatorId, String state);

    long countByState(String state);
}
