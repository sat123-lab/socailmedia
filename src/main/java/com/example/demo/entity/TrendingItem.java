package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One precomputed trending row. The {@link com.example.demo.service.TrendingService}
 * recalculates these every minute by summing weighted recent signals for
 * each (target_type, target_id) pair and applying an exponential decay.
 *
 * <p>Storing the result lets the API serve trending lists with a single
 * indexed query instead of recomputing from {@code feed_signals} on every
 * request — the typical "expensive write, cheap read" trade.</p>
 */
@Entity
@Table(name = "trending_items",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_trending",
                columnNames = {"target_type", "target_id", "target_label"}),
        indexes = {
                @Index(name = "idx_trending_type_score", columnList = "target_type,score"),
                @Index(name = "idx_trending_cat_score", columnList = "category,score"),
        })
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TrendingItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** HASHTAG, CREATOR, REEL, STORY, LIVE. */
    @Column(name = "target_type", nullable = false, length = 16)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    /** For HASHTAG rows the canonical tag goes here (target_id is null). */
    @Column(name = "target_label", length = 128)
    private String targetLabel;

    @Column(length = 64)
    private String category;

    @Column(nullable = false)
    @Builder.Default
    private double score = 0.0;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
