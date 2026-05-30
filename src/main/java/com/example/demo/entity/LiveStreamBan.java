package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Per-stream ban or mute. A {@code BAN} hides the viewer from the
 * stream altogether and revokes their chat; a {@code MUTE} keeps them
 * watching but silences their chat. Auto-cleaned when the stream is
 * deleted via the {@code ON DELETE CASCADE} constraint.
 */
@Entity
@Table(name = "live_stream_bans",
        uniqueConstraints = @UniqueConstraint(name = "uq_lsb_stream_viewer",
                columnNames = {"stream_id", "viewer_id"}))
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LiveStreamBan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stream_id")
    private LiveStream stream;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viewer_id")
    private User viewer;

    /** {@code BAN} or {@code MUTE}. */
    @Column(nullable = false, length = 8)
    @Builder.Default
    private String kind = "BAN";

    @Column(length = 256)
    private String reason;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
