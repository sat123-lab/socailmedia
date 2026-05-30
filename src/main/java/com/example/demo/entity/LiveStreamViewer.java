package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Active viewer presence row. Updated by viewer heartbeats every
 * {@link com.example.demo.service.LiveStreamService#HEARTBEAT_SECONDS}
 * seconds; rows older than {@code STALE_THRESHOLD_SECONDS} are reaped
 * by a server-side sweep so the live-viewer count always reflects who
 * is genuinely watching.
 *
 * <p>The {@code role} column distinguishes plain viewers, moderators
 * the creator promoted, and the creator themselves — handy for the
 * "👑 host", "🛡 mod", "👁 viewer" tags in the live chat UI.</p>
 */
@Entity
@Table(name = "live_stream_viewers",
        uniqueConstraints = @UniqueConstraint(name = "uq_lsv_stream_viewer",
                columnNames = {"stream_id", "viewer_id"}),
        indexes = @Index(name = "idx_lsv_last_seen",
                columnList = "stream_id,last_seen_at"))
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LiveStreamViewer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stream_id")
    private LiveStream stream;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "viewer_id")
    private User viewer;

    @Column(name = "joined_at", nullable = false)
    @Builder.Default
    private Instant joinedAt = Instant.now();

    @Column(name = "last_seen_at", nullable = false)
    @Builder.Default
    private Instant lastSeenAt = Instant.now();

    /** {@code VIEWER}, {@code MOD}, or {@code HOST}. */
    @Column(nullable = false, length = 16)
    @Builder.Default
    private String role = "VIEWER";
}
