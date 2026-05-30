package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One emoji reaction on a {@link ChatMessage}.
 *
 * <p>Reactions are intentionally per-(message, user, emoji) unique — a
 * single user can stack different emojis on the same message but cannot
 * spam the same emoji repeatedly. Toggling the same reaction simply
 * deletes the row.</p>
 *
 * <p>This table is the only place reaction counts come from; the
 * messaging hot path reads aggregate counts via a {@code GROUP BY emoji}
 * query so the row schema can evolve without touching the chat surface.</p>
 *
 * <h3>Why a separate table?</h3>
 * Storing reactions alongside the encrypted message body would either
 * leak metadata into the ciphertext or force the server to decrypt every
 * read. Keeping reactions in plaintext metadata respects the E2EE
 * boundary — the server <i>knows</i> a message was reacted to but
 * <i>still cannot</i> read its content.
 */
@Entity
@Table(name = "message_reactions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_message_reaction",
                columnNames = {"message_id", "user_id", "emoji"}),
        indexes = {
                @Index(name = "idx_reactions_message", columnList = "message_id"),
                @Index(name = "idx_reactions_user",    columnList = "user_id"),
        })
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MessageReaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    private String emoji;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
