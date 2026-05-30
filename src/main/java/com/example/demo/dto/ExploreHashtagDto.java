package com.example.demo.dto;

import com.example.demo.entity.Hashtag;
import com.example.demo.entity.TrendingItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Unified hashtag DTO that can come either from the
 * {@code hashtags} table (usage-count based) or from
 * {@code trending_items} (signal-decayed score).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExploreHashtagDto {

    private String tag;

    /** Usage count when this row originates from the hashtag table. */
    private long usageCount;

    /** Decayed signal score when this row originates from TrendingItem. */
    private double score;

    private String category;

    public static ExploreHashtagDto fromHashtag(Hashtag h) {
        return ExploreHashtagDto.builder()
                .tag(h.getTag())
                .usageCount(h.getUsageCount())
                .build();
    }

    public static ExploreHashtagDto fromTrend(TrendingItem t) {
        return ExploreHashtagDto.builder()
                .tag(t.getTargetLabel())
                .score(t.getScore())
                .category(t.getCategory())
                .build();
    }
}
