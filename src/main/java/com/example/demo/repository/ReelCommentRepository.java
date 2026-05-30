package com.example.demo.repository;

import com.example.demo.entity.ReelComment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ReelCommentRepository extends JpaRepository<ReelComment, Long> {
    List<ReelComment> findByReelIdOrderByCreatedAtDesc(Long reelId, Pageable pageable);
}
