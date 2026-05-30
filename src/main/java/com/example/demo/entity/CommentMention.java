package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Records that a comment mentions a specific user.
 *
 * <p>The mention table is independent of the comment text so the
 * notification fan-out, mention-feed query, and inline highlighting
 * can each work without re-parsing the body. Idempotent on
 * {@code (comment_id, mentioned_user_id)} so duplicate mentions of the
 * same user in one comment only generate a single notification.
 */
@Entity
@Table(name = "comment_mentions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_comment_mentions",
                        columnNames = {"comment_id", "mentioned_user_id"})
        },
        indexes = {
                @Index(name = "idx_comment_mentions_user",
                        columnList = "mentioned_user_id, created_at")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentMention {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Column(name = "mentioned_user_id", nullable = false)
    private Long mentionedUserId;

    /** Token as the author typed it — preserves casing for the inline chip. */
    @Column(length = 64, nullable = false)
    private String display;

    /** Character offset inside {@code Comment.text}. */
    @Column(name = "start_index", nullable = false)
    @Builder.Default
    private int startIndex = 0;

    @Column(name = "end_index", nullable = false)
    @Builder.Default
    private int endIndex = 0;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
