package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Per-user → creator affinity score. Equivalent to the user-interest
 * vector but on the people axis: the more positive interactions a user
 * has with a creator, the higher this score gets.
 *
 * <p>Used by:</p>
 * <ul>
 *   <li><b>Reel ranking</b> — boost reels from creators the user loves.</li>
 *   <li><b>Suggested creators</b> — collaborative-filter discovery of
 *       new creators similar to high-affinity ones.</li>
 *   <li><b>Profile ordering</b> — top of the search / mentions list.</li>
 * </ul>
 */
@Entity
@Table(name = "creator_affinity",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_creator_affinity", columnNames = {"user_id", "creator_id"}),
        indexes = @Index(name = "idx_ca_user_score", columnList = "user_id,score"))
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreatorAffinity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    @Column(nullable = false)
    @Builder.Default
    private double score = 0.0;

    @Column(name = "last_signal", nullable = false)
    @Builder.Default
    private Instant lastSignal = Instant.now();
}
