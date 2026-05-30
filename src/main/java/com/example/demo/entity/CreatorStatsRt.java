package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Per-creator realtime scoring snapshot. Updated by the recommendation
 * roll-up. Decoupled from the static {@link com.example.demo.dto.CreatorStatsDto}
 * (which lives on `/creator/stats` for dashboards) — this one's tuned for
 * <i>ranking</i>:
 *
 * <ul>
 *   <li><b>engagement_score</b> — recent (likes + comments + saves + shares)
 *       per impression, decayed.</li>
 *   <li><b>consistency_score</b> — how regularly the creator posts.
 *       Spiky one-shot creators score lower than steady weekly creators.</li>
 *   <li><b>quality_score</b> — combined; the primary boost factor used by
 *       the reel and home-feed rankers.</li>
 * </ul>
 */
@Entity
@Table(name = "creator_stats_rt",
        indexes = @Index(name = "idx_creator_stats_quality", columnList = "quality_score"))
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreatorStatsRt {

    @Id
    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "engagement_score", nullable = false)
    @Builder.Default
    private double engagementScore = 0.0;

    @Column(name = "consistency_score", nullable = false)
    @Builder.Default
    private double consistencyScore = 0.0;

    @Column(name = "quality_score", nullable = false)
    @Builder.Default
    private double qualityScore = 0.0;

    @Column(name = "posts_30d", nullable = false)
    @Builder.Default
    private int posts30d = 0;

    @Column(name = "reels_30d", nullable = false)
    @Builder.Default
    private int reels30d = 0;

    @Column(name = "lives_30d", nullable = false)
    @Builder.Default
    private int lives30d = 0;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
