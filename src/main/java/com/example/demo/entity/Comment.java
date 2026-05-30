package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "comments")

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class Comment {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )

    private Long id;

    // COMMENT TEXT

    @Column(
            length = 1000,
            nullable = false
    )

    private String text;

    // CREATED TIME

    private LocalDateTime createdAt;

    // USER

    @ManyToOne(fetch = FetchType.EAGER)

    @JoinColumn(
            name = "user_id",
            nullable = false
    )

    @JsonIgnoreProperties({
            "password"
    })

    private User user;

    // POST

    @ManyToOne(fetch = FetchType.LAZY)

    @JoinColumn(
            name = "post_id",
            nullable = false
    )

    @JsonBackReference

    private Post post;

    /** Null = top-level comment; otherwise reply to another comment on the same post. */
    @Column(name = "parent_id")
    private Long parentId;

    // -----------------------------------------------------------------
    // PHASE 1 — threading metadata
    // -----------------------------------------------------------------

    /**
     * Indent depth — 0 for top-level, 1 for direct replies, 2+ for
     * nested replies. The architecture is unlimited-depth; the UI
     * collapses everything past depth 2 into a single "Replies" bucket
     * the same way Instagram does.
     */
    @Column(nullable = false)
    @Builder.Default
    private int depth = 0;

    /**
     * Denormalised count of immediate children. Kept in sync by
     * {@code CommentService} on every reply create / soft delete so the
     * "View 12 replies" pill can render without an extra query.
     */
    @Column(name = "reply_count", nullable = false)
    @Builder.Default
    private int replyCount = 0;

    // -----------------------------------------------------------------
    // PHASE 5 — pinning  (one per post, enforced in CommentService)
    // -----------------------------------------------------------------

    @Column(nullable = false)
    @Builder.Default
    private boolean pinned = false;

    @Column(name = "pinned_at")
    private LocalDateTime pinnedAt;

    @Column(name = "pinned_by_user_id")
    private Long pinnedByUserId;

    // -----------------------------------------------------------------
    // PHASE 4 — edit + soft delete
    // -----------------------------------------------------------------

    /** Stamped every time the author edits. Null = never edited. */
    @Column(name = "edited_at")
    private LocalDateTime editedAt;

    /**
     * Soft delete preserves the thread shape so replies don't dangle.
     * The API returns these with {@code text = "[deleted]"} and the
     * author chrome stripped.
     */
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;

    // -----------------------------------------------------------------
    // PHASE 6 — sorting helper
    // -----------------------------------------------------------------

    /**
     * Engagement score recomputed on reaction add/remove. Used by the
     * "Top" sort. We re-compute on demand rather than via a trigger so
     * a stale cluster of comments simply orders less precisely — never
     * incorrectly.
     */
    @Column(name = "hot_score", nullable = false)
    @Builder.Default
    private double hotScore = 0d;

    // -----------------------------------------------------------------
    //  Read-side transients filled by CommentService.toDto(...)
    // -----------------------------------------------------------------

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Integer likesCount;

    @Transient
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean likedByMe;

    // AUTO TIME

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}