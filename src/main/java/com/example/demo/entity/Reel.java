package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A short-form vertical video (TikTok / Instagram Reels equivalent).
 *
 * <p>All counter fields are denormalized for read speed. They are bumped
 * atomically by repository {@code @Modifying} queries — no fetch-then-save
 * round-trips on the hot path.</p>
 */
@Entity
@Table(name = "reels", indexes = {
        @Index(name = "idx_reels_user",       columnList = "userId, createdAt"),
        @Index(name = "idx_reels_trending",   columnList = "trendingScore, createdAt"),
        @Index(name = "idx_reels_visibility", columnList = "visibility, deleted, createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(length = 2200)
    private String caption;

    @Column(nullable = false, length = 512)
    private String videoUrl;

    @Column(length = 512)
    private String thumbnailUrl;

    @Column(length = 512)
    private String audioUrl;

    @Column(length = 200)
    private String audioTitle;

    @Column(nullable = false)
    private int durationSeconds;

    private Integer width;
    private Integer height;

    @Builder.Default @Column(nullable = false) private int    likesCount        = 0;
    @Builder.Default @Column(nullable = false) private int    commentsCount     = 0;
    @Builder.Default @Column(nullable = false) private int    sharesCount       = 0;
    @Builder.Default @Column(nullable = false) private long   viewsCount        = 0;
    @Builder.Default @Column(nullable = false) private long   watchTimeSeconds  = 0;
    @Builder.Default @Column(nullable = false) private double trendingScore     = 0;

    @Column(length = 512)
    private String hashtags;

    /**
     * Optional overlay manifest produced by the Reels Editor — a JSON
     * blob describing text overlays, sticker positions, music timing,
     * transitions, and cover metadata that the player can re-render on
     * top of the raw {@link #videoUrl}. The server never inspects this;
     * it is opaque storage so future client builds can render new
     * overlay kinds without a backend migration.
     */
    @Column(name = "overlays_json", columnDefinition = "TEXT")
    private String overlaysJson;

    @Builder.Default
    @Column(nullable = false, length = 16)
    private String visibility = "PUBLIC"; // PUBLIC | FOLLOWERS | CLOSE_FRIENDS

    @Builder.Default
    @Column(nullable = false)
    private boolean deleted = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
