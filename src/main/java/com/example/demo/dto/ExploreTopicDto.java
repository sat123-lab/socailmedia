package com.example.demo.dto;

import com.example.demo.entity.UserInterest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Objects;

/**
 * A "suggested topic" shown on the Explore Hub — either a hashtag the
 * user has shown affinity for (from the interest graph) or a globally
 * trending category (cold-start fallback).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExploreTopicDto {

    private String topic;
    private double score;

    public static ExploreTopicDto fromInterest(UserInterest ui) {
        return new ExploreTopicDto(ui.getTopic(), ui.getScore());
    }

    /** Used by {@code Stream.distinct()} to dedupe by topic name. */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ExploreTopicDto t)) return false;
        return Objects.equals(topic, t.topic);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(topic);
    }
}
