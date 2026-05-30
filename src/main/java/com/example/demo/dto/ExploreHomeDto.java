package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Aggregated payload returned by {@code GET /explore/home}.
 *
 * Every section is independently nullable so the client can render
 * partial results when one upstream section is slow or empty — the
 * front-end already skeletons sections individually.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExploreHomeDto {

    /** Section 1 — Trending Reels (signal-decayed scored reels). */
    private List<ExploreReelDto> trendingReels;

    /** Section 2 — Personalised creator suggestions for the viewer. */
    private List<ExploreCreatorDto> recommendedCreators;

    /** Section 3 — Trending hashtags. */
    private List<ExploreHashtagDto> trendingHashtags;

    /** Section 4 — Live streams currently broadcasting. */
    private List<LiveStreamDto> liveNow;

    /**
     * Section 5 — Popular Stories. Header-only entries (avatar + name);
     * full items are fetched on tap.
     */
    private List<StoryGroupDto> popularStories;

    /** Section 6 — Recently joined creator/business accounts. */
    private List<ExploreCreatorDto> newCreators;

    /** Section 7 — Local trends. Currently mirrors global trends. */
    private List<ExploreHashtagDto> localTrends;

    /** Section 8 — Suggested topics based on the user's interest graph. */
    private List<ExploreTopicDto> suggestedTopics;

    /** Bonus — Trending audio tracks (derived from recent reel audio aggregation). */
    private List<ExploreMusicDto> trendingMusic;

    /**
     * Wall-clock when this payload was assembled — used by the client
     * to decide whether to soft-refresh vs hard-refresh.
     */
    private long generatedAtMs;
}
