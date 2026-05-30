package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Per-user view of a {@link Conversation} — pin / archive / mute / folder.
 *
 * <p>Conversations are a shared resource between two users, but every
 * "this conversation is pinned at the top of my list" or "I don't want
 * notifications from this chat for the next 8h" decision is strictly
 * <i>local</i> to one user. Storing those flags on the conversation row
 * itself would leak them across participants — this table keeps the
 * boundary clean.</p>
 *
 * <p>Indexed for the three hottest queries: pinned list, archived list,
 * and folder membership lookup.</p>
 */
@Entity
@Table(name = "conversation_settings",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_conv_setting",
                columnNames = {"user_id", "conversation_id"}),
        indexes = {
                @Index(name = "idx_conv_settings_user_pinned",
                       columnList = "user_id,pinned,pin_order"),
                @Index(name = "idx_conv_settings_user_archived",
                       columnList = "user_id,archived"),
                @Index(name = "idx_conv_settings_user_folder",
                       columnList = "user_id,folder_id"),
        })
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ConversationSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(nullable = false)
    @Builder.Default
    private boolean pinned = false;

    /** Manual order index for pinned chats. Lower = higher on the list. */
    @Column(name = "pin_order", nullable = false)
    @Builder.Default
    private int pinOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean archived = false;

    /** Mute timeout — when set, push notifications are suppressed until
     *  this instant; null = unmuted; far-future = muted forever. */
    @Column(name = "muted_until")
    private Instant mutedUntil;

    /** Foreign key into {@link ChatFolder} — null means "no folder /
     *  All chats". Hard FK omitted on purpose so folder deletion can
     *  null this out lazily. */
    @Column(name = "folder_id")
    private Long folderId;

    /** ALL | MENTIONS | NONE — finer-grained notification gate. */
    @Column(nullable = false, length = 16)
    @Builder.Default
    private String notifications = "ALL";

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();
}
