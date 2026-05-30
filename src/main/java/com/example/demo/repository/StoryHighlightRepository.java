package com.example.demo.repository;

import com.example.demo.entity.StoryHighlight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoryHighlightRepository extends JpaRepository<StoryHighlight, Long> {
    List<StoryHighlight> findByUserIdOrderByCreatedAtDesc(Long userId);
}
