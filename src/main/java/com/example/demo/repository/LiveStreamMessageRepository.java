package com.example.demo.repository;

import com.example.demo.entity.LiveStreamMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LiveStreamMessageRepository extends JpaRepository<LiveStreamMessage, Long> {

    Page<LiveStreamMessage> findByStream_IdAndDeletedFalseOrderByCreatedAtDesc(
            Long streamId, Pageable pageable);

    Optional<LiveStreamMessage> findFirstByStream_IdAndSender_IdOrderByCreatedAtDesc(
            Long streamId, Long senderId);

    List<LiveStreamMessage> findTop50ByStream_IdAndPinnedTrueOrderByCreatedAtDesc(Long streamId);

    long countByStream_IdAndSender_IdAndCreatedAtAfter(Long streamId, Long senderId, Instant since);
}
