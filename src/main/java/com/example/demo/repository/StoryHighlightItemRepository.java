package com.example.demo.repository;

import com.example.demo.entity.StoryHighlightItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoryHighlightItemRepository extends JpaRepository<StoryHighlightItem, Long> {
    List<StoryHighlightItem> findByHighlightIdOrderBySortOrderAsc(Long highlightId);
}
