package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One emoji reaction by one user on one comment.
 *
 * <p>The unique index on {@code (comment_id, user_id)} enforces "one
 * reaction per user per comment" — switching emoji is an UPDATE on the
 * existing row, not a second insert. The service layer also mirrors
 * {@code LIKE} reactions into the legacy {@code comment_likes} table
 * so the original {@code /comments/like/...} endpoints stay correct
 * without a flag day.
 */
@Entity
@Table(name = "comment_reactions",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_comment_reactions_user",
                        columnNames = {"comment_id", "user_id"})
        },
        indexes = {
                @Index(name = "idx_comment_reactions_emoji",
                        columnList = "comment_id, emoji")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private CommentReactionType emoji;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
