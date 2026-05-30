package com.example.demo.repository;

import com.example.demo.entity.LiveStreamBan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LiveStreamBanRepository extends JpaRepository<LiveStreamBan, Long> {
    Optional<LiveStreamBan> findByStream_IdAndViewer_Id(Long streamId, Long viewerId);
    void deleteByStream_IdAndViewer_Id(Long streamId, Long viewerId);
}
