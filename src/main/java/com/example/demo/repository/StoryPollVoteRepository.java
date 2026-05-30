package com.example.demo.repository;

import com.example.demo.entity.StoryPollVote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StoryPollVoteRepository extends JpaRepository<StoryPollVote, Long> {
    Optional<StoryPollVote> findByPollIdAndUserId(Long pollId, Long userId);
}
