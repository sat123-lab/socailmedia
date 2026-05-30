package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Aggregated metrics surfaced on the user's professional dashboard.
 * Backed by a single SQL roll-up query in {@link com.example.demo.service.CreatorService}.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreatorStatsDto {
    private Long userId;
    private String accountType;
    private boolean verified;

    private long postsCount;
    private long reelsCount;
    private long storiesCount;

    private long totalLikes;
    private long totalComments;
    private long totalShares;
    private long totalReelViews;

    private long followers;
    private long following;

    /** Engagement % = (likes + comments) / max(views, 1). */
    private double engagementRate;
}
