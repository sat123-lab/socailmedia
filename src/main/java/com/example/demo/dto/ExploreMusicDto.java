package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One trending audio track, derived by grouping recent reels by
 * {@code audioUrl}. We don't have a dedicated music catalog yet, so
 * the {@code id} returned by the API is the audio URL itself — clients
 * use it as both the play URL and the cache key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExploreMusicDto {
    /** Doubles as the play URL until a separate music catalog ships. */
    private String audioUrl;
    private String title;
    private long usageCount;
    private double trendingScore;
    /** A representative thumbnail from one of the reels using the track. */
    private String thumbnailUrl;
}
