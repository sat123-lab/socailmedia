package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One live-stream session. A row stays around after the broadcast ends
 * so the analytics surfaces ("peak viewers", "duration", "messages")
 * can keep showing it on the creator's dashboard. The {@code state}
 * column distinguishes {@code LIVE}, {@code ENDED}, and {@code BANNED}.
 *
 * <p>The {@code rtcChannel} + {@code rtcToken} pair is intentionally
 * opaque to the rest of the application — they're whatever the chosen
 * media transport (WebRTC SFU room id, HLS endpoint, …) needs to wire
 * client SDKs to. The application logic (chat, reactions, viewer
 * presence) does not care about its content.</p>
 */
@Entity
@Table(name = "live_streams", indexes = {
        @Index(name = "idx_live_streams_state_started", columnList = "state,started_at"),
        @Index(name = "idx_live_streams_creator", columnList = "creator_id,started_at"),
        @Index(name = "idx_live_streams_category", columnList = "category,state,started_at"),
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LiveStream {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id")
    private User creator;

    @Column(length = 160)
    private String title;

    @Column(length = 64)
    private String category;

    @Column(name = "thumbnail_url", length = 512)
    private String thumbnailUrl;

    @Column(name = "rtc_channel", length = 128)
    private String rtcChannel;

    @Column(name = "rtc_token", length = 512)
    private String rtcToken;

    /** {@code PUBLIC} or {@code CLOSE_FRIENDS}. */
    @Column(nullable = false, length = 16)
    @Builder.Default
    private String privacy = "PUBLIC";

    /** {@code LIVE}, {@code ENDED}, or {@code BANNED}. */
    @Column(nullable = false, length = 16)
    @Builder.Default
    private String state = "LIVE";

    /** Seconds between two messages from the same viewer. {@code 0} disables. */
    @Column(name = "slow_mode_sec", nullable = false)
    @Builder.Default
    private Integer slowModeSec = 0;

    @Column(name = "peak_viewers", nullable = false)
    @Builder.Default
    private Integer peakViewers = 0;

    @Column(name = "total_viewers", nullable = false)
    @Builder.Default
    private Integer totalViewers = 0;

    @Column(name = "likes_count", nullable = false)
    @Builder.Default
    private Integer likesCount = 0;

    @Column(name = "messages_count", nullable = false)
    @Builder.Default
    private Integer messagesCount = 0;

    @Column(name = "started_at", nullable = false)
    @Builder.Default
    private Instant startedAt = Instant.now();

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "pinned_message", length = 280)
    private String pinnedMessage;
}
