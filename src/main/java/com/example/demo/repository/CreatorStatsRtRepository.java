package com.example.demo.repository;

import com.example.demo.entity.CreatorStatsRt;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CreatorStatsRtRepository extends JpaRepository<CreatorStatsRt, Long> {
    List<CreatorStatsRt> findByOrderByQualityScoreDesc(Pageable page);
}
