package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One row per user describing their realtime presence: online state,
 * last-seen wall clock, and whether they're currently typing (or
 * recording a voice note) in a specific conversation.
 *
 * <p>The {@code typing_until} column is the "natural lifetime" of the
 * typing indicator — set by the client to a few seconds in the future
 * every keystroke. The reaper job (and any lookup query) treats
 * {@code typing_until < now()} as "not typing" without needing to
 * actually clear the row, so we avoid a write per keystroke-stop.</p>
 *
 * <p>{@code last_seen_privacy} mirrors WhatsApp's three-state privacy
 * setting so the server can refuse to leak last_seen to peers the user
 * doesn't follow / hasn't allowed.</p>
 */
@Entity
@Table(name = "user_presence",
        indexes = @Index(name = "idx_presence_online",
                         columnList = "online,last_seen_at"))
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserPresence {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false)
    @Builder.Default
    private boolean online = false;

    @Column(name = "last_seen_at", nullable = false)
    @Builder.Default
    private Instant lastSeenAt = Instant.now();

    @Column(name = "typing_in_conv_id")
    private Long typingInConvId;

    /** TEXT | VOICE — what the typing indicator should display. */
    @Column(name = "typing_kind", length = 16)
    private String typingKind;

    @Column(name = "typing_until")
    private Instant typingUntil;

    /** EVERYONE | CONTACTS | NOBODY — gate for the last_seen broadcast. */
    @Column(name = "last_seen_privacy", nullable = false, length = 16)
    @Builder.Default
    private String lastSeenPrivacy = "EVERYONE";
}
