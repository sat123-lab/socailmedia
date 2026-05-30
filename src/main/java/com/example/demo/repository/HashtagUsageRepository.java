package com.example.demo.repository;

import com.example.demo.entity.HashtagUsage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface HashtagUsageRepository extends JpaRepository<HashtagUsage, Long> {
    List<HashtagUsage> findByTagOrderByCreatedAtDesc(String tag, Pageable pageable);
}
