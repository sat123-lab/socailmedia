package com.example.demo.repository;

import com.example.demo.entity.LiveStreamViewer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LiveStreamViewerRepository extends JpaRepository<LiveStreamViewer, Long> {

    Optional<LiveStreamViewer> findByStream_IdAndViewer_Id(Long streamId, Long viewerId);

    long countByStream_IdAndLastSeenAtAfter(Long streamId, Instant since);

    List<LiveStreamViewer> findTop50ByStream_IdAndLastSeenAtAfterOrderByLastSeenAtDesc(
            Long streamId, Instant since);

    @Modifying
    @Transactional
    @Query("DELETE FROM LiveStreamViewer v WHERE v.lastSeenAt < :cutoff")
    int reapStale(@Param("cutoff") Instant cutoff);

    @Modifying
    @Transactional
    @Query("DELETE FROM LiveStreamViewer v WHERE v.stream.id = :streamId")
    void deleteByStream(@Param("streamId") Long streamId);
}
