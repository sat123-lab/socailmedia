package com.example.demo.dto;

import com.example.demo.entity.LiveStream;
import com.example.demo.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.Instant;

/**
 * Wire-friendly projection of a {@link LiveStream}. Two view modes:
 *
 * <ul>
 *   <li><b>Summary</b> — for discovery cards. Joins-safe (no rtcToken).</li>
 *   <li><b>Detail</b>  — returned when a viewer or the creator opens a
 *                       stream and needs the transport channel token.</li>
 * </ul>
 *
 * The same DTO shape is used everywhere so the Flutter side has one model.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LiveStreamDto {

    private Long id;
    private Long creatorId;
    private String creatorName;
    private String creatorAvatar;
    private boolean creatorVerified;

    private String title;
    private String category;
    private String thumbnailUrl;

    private String privacy;
    private String state;

    private String rtcChannel; // null on summary endpoints
    private String rtcToken;   // null on summary endpoints

    private int currentViewers;
    private int peakViewers;
    private int totalViewers;
    private int likesCount;
    private int messagesCount;
    private int slowModeSec;

    private String pinnedMessage;

    private Instant startedAt;
    private Instant endedAt;

    /** Seconds the stream has been running so the client doesn't recompute. */
    private long durationSeconds;

    public static LiveStreamDto summary(LiveStream s, int currentViewers) {
        return base(s, currentViewers, false);
    }

    public static LiveStreamDto detail(LiveStream s, int currentViewers) {
        return base(s, currentViewers, true);
    }

    private static LiveStreamDto base(LiveStream s, int currentViewers, boolean withToken) {
        User c = s.getCreator();
        Instant end = s.getEndedAt() == null ? Instant.now() : s.getEndedAt();
        long secs = Math.max(0, Duration.between(s.getStartedAt(), end).getSeconds());
        return LiveStreamDto.builder()
                .id(s.getId())
                .creatorId(c == null ? null : c.getId())
                .creatorName(c == null ? null : c.getName())
                .creatorAvatar(c == null ? null : c.getProfileImage())
                .creatorVerified(c != null && c.isVerified())
                .title(s.getTitle())
                .category(s.getCategory())
                .thumbnailUrl(s.getThumbnailUrl())
                .privacy(s.getPrivacy())
                .state(s.getState())
                .rtcChannel(withToken ? s.getRtcChannel() : null)
                .rtcToken(withToken ? s.getRtcToken() : null)
                .currentViewers(currentViewers)
                .peakViewers(s.getPeakViewers() == null ? 0 : s.getPeakViewers())
                .totalViewers(s.getTotalViewers() == null ? 0 : s.getTotalViewers())
                .likesCount(s.getLikesCount() == null ? 0 : s.getLikesCount())
                .messagesCount(s.getMessagesCount() == null ? 0 : s.getMessagesCount())
                .slowModeSec(s.getSlowModeSec() == null ? 0 : s.getSlowModeSec())
                .pinnedMessage(s.getPinnedMessage())
                .startedAt(s.getStartedAt())
                .endedAt(s.getEndedAt())
                .durationSeconds(secs)
                .build();
    }
}
