package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row in the user's "interest vector" — how much affinity the user
 * has for a given topic (hashtag or category id). Maintained by the
 * {@link com.example.demo.service.InterestGraphService} as an
 * exponentially-decayed sum so recent interactions matter more.
 */
@Entity
@Table(name = "user_interests",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_user_interests", columnNames = {"user_id", "topic"}),
        indexes = @Index(name = "idx_ui_user_score", columnList = "user_id,score"))
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserInterest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Normalised hashtag or category id (lower-case, no leading '#'). */
    @Column(nullable = false, length = 64)
    private String topic;

    @Column(nullable = false)
    @Builder.Default
    private double score = 0.0;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
