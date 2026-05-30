package com.example.demo.repository;

import com.example.demo.entity.StoryView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StoryViewRepository extends JpaRepository<StoryView, Long> {

    boolean existsByStoryIdAndViewerId(Long storyId, Long viewerId);

    List<StoryView> findByViewerIdAndStoryIdIn(
            Long viewerId,
            List<Long> storyIds
    );

    void deleteByStoryId(Long storyId);
}
