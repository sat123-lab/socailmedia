package com.example.demo.repository;

import com.example.demo.entity.StoryPoll;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface StoryPollRepository extends JpaRepository<StoryPoll, Long> {

    List<StoryPoll> findByStoryId(Long storyId);

    @Modifying @Transactional
    @Query("UPDATE StoryPoll p SET p.votesA = p.votesA + 1 WHERE p.id = :id")
    int bumpA(@Param("id") Long id);

    @Modifying @Transactional
    @Query("UPDATE StoryPoll p SET p.votesB = p.votesB + 1 WHERE p.id = :id")
    int bumpB(@Param("id") Long id);
}
