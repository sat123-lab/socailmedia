package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One interaction the user just made — the universal raw "training" event
 * for the recommendation engine. Every screen that wants to teach the
 * recommender about a tap, watch, or follow writes a row here.
 *
 * <h3>Why an append-only event log?</h3>
 * <ul>
 *   <li>Decoupling — call sites don't care which scores get updated.</li>
 *   <li>Auditable — easy to A/B a new ranking model by replaying signals.</li>
 *   <li>Cheap writes — INSERT only, no contention on rolling-up tables.</li>
 *   <li>Future-proof — replace the roll-up with a real ML pipeline by
 *       streaming this table to Kafka/Snowflake without changing call sites.</li>
 * </ul>
 *
 * The {@code weight} column lets us teach the model that some signals are
 * stronger than others (e.g. SAVE/COMPLETE_REEL = 3, LIKE = 1, VIEW = 0.5,
 * SKIP = -1). Negative weights are allowed and used by the SKIP / REPORT
 * paths to push items down in the rank.
 */
@Entity
@Table(name = "feed_signals", indexes = {
        @Index(name = "idx_feed_signals_user_time", columnList = "user_id,created_at"),
        @Index(name = "idx_feed_signals_target", columnList = "target_type,target_id,created_at"),
        @Index(name = "idx_feed_signals_creator", columnList = "creator_id,created_at"),
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FeedSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** VIEW, LIKE, COMMENT, SHARE, SAVE, FOLLOW, COMPLETE_REEL, REWATCH, SKIP, REPORT, PROFILE_VISIT, LIVE_JOIN. */
    @Column(nullable = false, length = 32)
    private String kind;

    /** POST, REEL, STORY, LIVE, USER, HASHTAG. */
    @Column(name = "target_type", nullable = false, length = 16)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    /** Used when target_id isn't applicable (e.g. a hashtag string). */
    @Column(name = "target_label", length = 128)
    private String targetLabel;

    @Column(nullable = false)
    @Builder.Default
    private double weight = 1.0;

    /** Denormalized author of the target — saves a join on every roll-up read. */
    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
