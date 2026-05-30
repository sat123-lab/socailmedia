package com.example.demo.repository;

import com.example.demo.entity.StoryReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Map;

public interface StoryReactionRepository extends JpaRepository<StoryReaction, Long> {

    List<StoryReaction> findByStoryId(Long storyId);

    @Query("""
           SELECT new map(r.emoji as emoji, COUNT(r) as count)
           FROM StoryReaction r
           WHERE r.storyId = :storyId
           GROUP BY r.emoji
           ORDER BY COUNT(r) DESC
           """)
    List<Map<String, Object>> summary(@Param("storyId") Long storyId);
}
