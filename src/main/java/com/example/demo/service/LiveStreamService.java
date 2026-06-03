package com.example.demo.service;

import com.example.demo.dto.LiveMessageDto;
import com.example.demo.dto.LiveStreamDto;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core logic for the live-streaming subsystem.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Lifecycle — start a stream, end it, fetch summary / detail.</li>
 *   <li>Discovery — trending list, followed-creators-live, category filter.</li>
 *   <li>Chat — send + persist + fan out, respecting slow-mode and per-user
 *       rate limits, with ban / mute enforcement.</li>
 *   <li>Reactions — fan out to STOMP, bump the aggregated counter.</li>
 *   <li>Viewer presence — heartbeat upsert + periodic stale-row reaper.</li>
 *   <li>Moderation — ban, mute, kick, pin / unpin messages.</li>
 *   <li>Gifts — record a {@link LiveStreamGift} and fan-out a "gift" event
 *       so the receiver-side overlay can animate.</li>
 * </ul>
 *
 * Chat / reaction / viewer-count events are pushed via STOMP topics:
 * <ul>
 *   <li>{@code /topic/live/{streamId}/chat}     — new messages, pins, deletes</li>
 *   <li>{@code /topic/live/{streamId}/react}    — reaction emoji events</li>
 *   <li>{@code /topic/live/{streamId}/viewers}  — periodic viewer-count updates</li>
 *   <li>{@code /topic/live/{streamId}/state}    — state changes (slow mode, end)</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LiveStreamService {

    public static final int HEARTBEAT_SECONDS = 15;
    public static final int STALE_THRESHOLD_SECONDS = 35;

    /** Hard cap on per-viewer chat messages per minute to soak up bot floods. */
    private static final long CHAT_FLOOD_LIMIT_PER_MINUTE = 30;

    private final LiveStreamRepository streamRepo;
    private final LiveStreamMessageRepository messageRepo;
    private final LiveStreamViewerRepository viewerRepo;
    private final LiveStreamBanRepository banRepo;
    private final LiveStreamGiftRepository giftRepo;
    private final UserRepository userRepo;
    private final RealtimeEventService realtime;

    private final SecureRandom secureRandom = new SecureRandom();

    // ---------------------------------------------------------------
    //  Lifecycle
    // ---------------------------------------------------------------

    @Transactional
    public LiveStreamDto start(Long creatorId, StartRequest req) {
        User creator = userRepo.findById(creatorId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown creator"));

        // Auto-end any previous LIVE row from the same creator so they
        // can't have two live sessions in flight at once.
        streamRepo.findFirstByCreator_IdAndStateOrderByStartedAtDesc(creatorId, "LIVE")
                .ifPresent(prev -> doEnd(prev, "Replaced by new session"));

        LiveStream s = LiveStream.builder()
                .creator(creator)
                .title(req.title)
                .category(req.category)
                .thumbnailUrl(req.thumbnailUrl)
                .privacy(req.privacy == null ? "PUBLIC" : req.privacy)
                .state("LIVE")
                .rtcChannel(secureChannel())
                .rtcToken(secureToken())
                .startedAt(Instant.now())
                .build();
        s = streamRepo.save(s);

        // Creator is always implicitly a HOST viewer so the chat side-rail
        // shows the right tags and our presence reaper doesn't kill them.
        upsertPresence(s, creator, "HOST");

        log.info("Live stream started id={} creator={} title={}",
                s.getId(), creatorId, s.getTitle());

        realtime.toRoom("live-" + s.getId(), "live.state",
                Map.of("state", "LIVE", "id", s.getId()));
        return LiveStreamDto.detail(s, 1);
    }

    @Transactional
    public LiveStreamDto end(Long streamId, Long requestingUserId) {
        LiveStream s = streamRepo.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found"));
        if (!Objects.equals(s.getCreator().getId(), requestingUserId)) {
            throw new SecurityException("Only the creator can end this stream");
        }
        doEnd(s, "Creator ended the stream");
        return LiveStreamDto.detail(s, 0);
    }

    private void doEnd(LiveStream s, String reason) {
        s.setState("ENDED");
        s.setEndedAt(Instant.now());
        streamRepo.save(s);
        viewerRepo.deleteByStream(s.getId());
        realtime.toRoom("live-" + s.getId(), "live.state",
                Map.of("state", "ENDED", "reason", reason == null ? "" : reason));
    }

    @Transactional(readOnly = true)
    public LiveStreamDto detail(Long streamId, Long requestingUserId) {
        LiveStream s = streamRepo.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found"));

        // Ban gate — banned viewers must not see the transport token even
        // if they brute-force the id.
        if (requestingUserId != null) {
            banRepo.findByStream_IdAndViewer_Id(streamId, requestingUserId)
                    .ifPresent(b -> {
                        if ("BAN".equals(b.getKind())) {
                            throw new SecurityException("You are banned from this stream");
                        }
                    });
        }
        return LiveStreamDto.detail(s, currentViewerCount(streamId));
    }

    // ---------------------------------------------------------------
    //  Discovery
    // ---------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<LiveStreamDto> trending(String category, int limit) {
        Pageable page = PageRequest.of(0, Math.min(50, Math.max(1, limit)));
        Page<LiveStream> p = streamRepo.trendingWithCategory(category, page);
        return p.stream()
                .map(s -> LiveStreamDto.summary(s, currentViewerCount(s.getId())))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LiveStreamDto> followedLive(Long userId) {
        if (userId == null) return List.of();
        return streamRepo.followedLive(userId).stream()
                .map(s -> LiveStreamDto.summary(s, currentViewerCount(s.getId())))
                .collect(Collectors.toList());
    }

    // ---------------------------------------------------------------
    //  Viewer presence
    // ---------------------------------------------------------------

    @Transactional
    public LiveStreamDto join(Long streamId, Long viewerId) {
        LiveStream s = streamRepo.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found"));
        if (!"LIVE".equals(s.getState())) {
            throw new IllegalStateException("Stream is no longer live");
        }
        User v = userRepo.findById(viewerId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown viewer"));

        banRepo.findByStream_IdAndViewer_Id(streamId, viewerId).ifPresent(b -> {
            if ("BAN".equals(b.getKind())) {
                throw new SecurityException("You are banned from this stream");
            }
        });

        boolean firstTime = viewerRepo
                .findByStream_IdAndViewer_Id(streamId, viewerId).isEmpty();
        upsertPresence(s, v, Objects.equals(s.getCreator().getId(), viewerId)
                ? "HOST" : "VIEWER");

        if (firstTime) {
            s.setTotalViewers((s.getTotalViewers() == null ? 0 : s.getTotalViewers()) + 1);
            streamRepo.save(s);

            // System "joined" chip in the chat rail.
            LiveStreamMessage joinMsg = LiveStreamMessage.builder()
                    .stream(s)
                    .sender(v)
                    .body(v.getName() + " joined")
                    .kind("JOIN")
                    .build();
            messageRepo.save(joinMsg);
            realtime.toRoom("live-" + streamId, "live.chat",
                    Map.of("message", LiveMessageDto.of(joinMsg)));
        }

        bumpPeakIfNeeded(s);
        return LiveStreamDto.detail(s, currentViewerCount(streamId));
    }

    @Transactional
    public void heartbeat(Long streamId, Long viewerId) {
        viewerRepo.findByStream_IdAndViewer_Id(streamId, viewerId).ifPresent(v -> {
            v.setLastSeenAt(Instant.now());
            viewerRepo.save(v);
        });
    }

    @Transactional
    public void leave(Long streamId, Long viewerId) {
        viewerRepo.findByStream_IdAndViewer_Id(streamId, viewerId)
                .ifPresent(viewerRepo::delete);
    }

    private void upsertPresence(LiveStream stream, User viewer, String role) {
        LiveStreamViewer presence = viewerRepo
                .findByStream_IdAndViewer_Id(stream.getId(), viewer.getId())
                .orElseGet(() -> LiveStreamViewer.builder()
                        .stream(stream)
                        .viewer(viewer)
                        .joinedAt(Instant.now())
                        .role(role)
                        .build());
        presence.setRole(role);
        presence.setLastSeenAt(Instant.now());
        viewerRepo.save(presence);
    }

    private int currentViewerCount(Long streamId) {
        Instant cutoff = Instant.now().minusSeconds(STALE_THRESHOLD_SECONDS);
        return (int) viewerRepo.countByStream_IdAndLastSeenAtAfter(streamId, cutoff);
    }

    private void bumpPeakIfNeeded(LiveStream s) {
        int current = currentViewerCount(s.getId());
        if (current > (s.getPeakViewers() == null ? 0 : s.getPeakViewers())) {
            s.setPeakViewers(current);
            streamRepo.save(s);
        }
    }

    /**
     * Sweeps stale presence rows + pushes a fresh viewer-count event per
     * live stream. Runs on a slow drumbeat to keep the room counts honest
     * even when clients drop without saying goodbye.
     */
    @Scheduled(fixedDelay = 15_000)
    public void reaperTick() {
        Instant cutoff = Instant.now().minusSeconds(STALE_THRESHOLD_SECONDS);
        try {
            int killed = viewerRepo.reapStale(cutoff);
            if (killed > 0) log.debug("Reaped {} stale live-stream viewers", killed);

            // Push a fresh count for every still-live stream — cheap because
            // we only count currently-live rooms, which is a small set.
            for (LiveStream s : streamRepo.trending(PageRequest.of(0, 50))) {
                int count = currentViewerCount(s.getId());
                bumpPeakIfNeeded(s);
                realtime.toRoom("live-" + s.getId(), "live.viewers",
                        Map.of("streamId", s.getId(), "count", count));
            }
        } catch (Exception e) {
            log.warn("Live stream reaper tick failed: {}", e.getMessage());
        }
    }

    // ---------------------------------------------------------------
    //  Chat
    // ---------------------------------------------------------------

    @Transactional
    public LiveMessageDto sendChat(Long streamId, Long senderId, String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Message body required");
        }
        if (body.length() > 280) {
            body = body.substring(0, 280);
        }

        LiveStream s = streamRepo.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found"));
        if (!"LIVE".equals(s.getState())) {
            throw new IllegalStateException("Stream is no longer live");
        }

        // Mute / ban gate.
        banRepo.findByStream_IdAndViewer_Id(streamId, senderId).ifPresent(b -> {
            throw new SecurityException("You cannot chat in this stream");
        });

        // Slow mode + flood limit.
        Instant since = Instant.now().minus(Duration.ofMinutes(1));
        long perMinute = messageRepo
                .countByStream_IdAndSender_IdAndCreatedAtAfter(streamId, senderId, since);
        if (perMinute >= CHAT_FLOOD_LIMIT_PER_MINUTE) {
            throw new IllegalStateException("Slow down — too many messages");
        }
        int slow = s.getSlowModeSec() == null ? 0 : s.getSlowModeSec();
        if (slow > 0) {
            messageRepo.findFirstByStream_IdAndSender_IdOrderByCreatedAtDesc(streamId, senderId)
                    .ifPresent(last -> {
                        if (Duration.between(last.getCreatedAt(), Instant.now())
                                .getSeconds() < slow) {
                            throw new IllegalStateException("Slow mode is on — wait " + slow + "s");
                        }
                    });
        }

        User sender = userRepo.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown sender"));

        LiveStreamMessage m = LiveStreamMessage.builder()
                .stream(s).sender(sender)
                .body(body)
                .kind("CHAT")
                .build();
        m = messageRepo.save(m);

        s.setMessagesCount((s.getMessagesCount() == null ? 0 : s.getMessagesCount()) + 1);
        streamRepo.save(s);

        LiveMessageDto dto = LiveMessageDto.of(m);
        realtime.toRoom("live-" + streamId, "live.chat", Map.of("message", dto));
        return dto;
    }

    @Transactional(readOnly = true)
    public List<LiveMessageDto> recentMessages(Long streamId, int limit) {
        Pageable page = PageRequest.of(0, Math.min(200, Math.max(1, limit)));
        return messageRepo
                .findByStream_IdAndDeletedFalseOrderByCreatedAtDesc(streamId, page)
                .stream()
                .map(LiveMessageDto::of)
                .collect(Collectors.toList());
    }

    @Transactional
    public void pinMessage(Long streamId, Long messageId, Long creatorId) {
        LiveStream s = streamRepo.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found"));
        if (!Objects.equals(s.getCreator().getId(), creatorId)) {
            throw new SecurityException("Only the creator can pin messages");
        }
        LiveStreamMessage m = messageRepo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        m.setPinned(true);
        messageRepo.save(m);
        s.setPinnedMessage(m.getBody());
        streamRepo.save(s);
        realtime.toRoom("live-" + streamId, "live.chat.pinned",
                Map.of("message", LiveMessageDto.of(m)));
    }

    @Transactional
    public void deleteMessage(Long streamId, Long messageId, Long requestingUserId) {
        LiveStream s = streamRepo.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found"));
        LiveStreamMessage m = messageRepo.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Message not found"));
        boolean isCreator = Objects.equals(s.getCreator().getId(), requestingUserId);
        boolean isOwner   = Objects.equals(m.getSender().getId(), requestingUserId);
        if (!isCreator && !isOwner) {
            throw new SecurityException("Not allowed to delete this message");
        }
        m.setDeleted(true);
        messageRepo.save(m);
        realtime.toRoom("live-" + streamId, "live.chat.deleted",
                Map.of("messageId", messageId));
    }

    // ---------------------------------------------------------------
    //  Reactions
    // ---------------------------------------------------------------

    @Transactional
    public void react(Long streamId, Long viewerId, String emoji, int count) {
        if (emoji == null || emoji.isBlank()) return;
        if (count <= 0) count = 1;
        if (count > 20) count = 20;
        LiveStream s = streamRepo.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found"));
        if (!"LIVE".equals(s.getState())) return;

        // Ban / mute gate — silent reject so the client doesn't ping.
        if (banRepo.findByStream_IdAndViewer_Id(streamId, viewerId).isPresent()) {
            return;
        }

        s.setLikesCount((s.getLikesCount() == null ? 0 : s.getLikesCount()) + count);
        streamRepo.save(s);

        realtime.toRoom("live-" + streamId, "live.react",
                Map.of("emoji", emoji, "count", count, "viewerId", viewerId));
    }

    // ---------------------------------------------------------------
    //  Moderation
    // ---------------------------------------------------------------

    @Transactional
    public void ban(Long streamId, Long viewerId, Long creatorId, String kind, String reason) {
        LiveStream s = streamRepo.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found"));
        if (!Objects.equals(s.getCreator().getId(), creatorId)) {
            throw new SecurityException("Only the creator can moderate this stream");
        }
        String k = "MUTE".equalsIgnoreCase(kind) ? "MUTE" : "BAN";
        LiveStreamBan b = banRepo.findByStream_IdAndViewer_Id(streamId, viewerId)
                .orElseGet(() -> LiveStreamBan.builder()
                        .stream(s)
                        .viewer(userRepo.getReferenceById(viewerId))
                        .build());
        b.setKind(k);
        b.setReason(reason);
        banRepo.save(b);

        if ("BAN".equals(k)) {
            viewerRepo.findByStream_IdAndViewer_Id(streamId, viewerId)
                    .ifPresent(viewerRepo::delete);
            realtime.toRoom("live-" + streamId, "live.ban",
                    Map.of("viewerId", viewerId, "reason", reason == null ? "" : reason));
        } else {
            realtime.toRoom("live-" + streamId, "live.mute",
                    Map.of("viewerId", viewerId));
        }
    }

    @Transactional
    public void unban(Long streamId, Long viewerId, Long creatorId) {
        LiveStream s = streamRepo.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found"));
        if (!Objects.equals(s.getCreator().getId(), creatorId)) {
            throw new SecurityException("Only the creator can moderate this stream");
        }
        banRepo.deleteByStream_IdAndViewer_Id(streamId, viewerId);
    }

    @Transactional
    public void setSlowMode(Long streamId, Long creatorId, int seconds) {
        LiveStream s = streamRepo.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found"));
        if (!Objects.equals(s.getCreator().getId(), creatorId)) {
            throw new SecurityException("Only the creator can change slow mode");
        }
        s.setSlowModeSec(Math.max(0, Math.min(300, seconds)));
        streamRepo.save(s);
        realtime.toRoom("live-" + streamId, "live.state",
                Map.of("slowModeSec", s.getSlowModeSec()));
    }

    // ---------------------------------------------------------------
    //  Gifts (monetization architecture)
    // ---------------------------------------------------------------

    @Transactional
    public void sendGift(Long streamId, Long senderId, String giftId, int value) {
        LiveStream s = streamRepo.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found"));
        if (!"LIVE".equals(s.getState())) {
            throw new IllegalStateException("Stream is no longer live");
        }
        User sender = userRepo.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown sender"));

        LiveStreamGift g = LiveStreamGift.builder()
                .stream(s)
                .sender(sender)
                .creator(s.getCreator())
                .giftId(giftId)
                .giftValue(Math.max(0, value))
                .build();
        giftRepo.save(g);

        realtime.toRoom("live-" + streamId, "live.gift", Map.of(
                "giftId", giftId,
                "value", g.getGiftValue(),
                "senderId", senderId,
                "senderName", sender.getName()));
    }

    @Transactional(readOnly = true)
    public long totalGiftValue(Long streamId) {
        return giftRepo.sumValueByStream(streamId);
    }

    // ---------------------------------------------------------------
    //  Internals
    // ---------------------------------------------------------------

    private String secureChannel() {
        byte[] buf = new byte[12];
        secureRandom.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    private String secureToken() {
        byte[] buf = new byte[32];
        secureRandom.nextBytes(buf);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buf);
    }

    // ---------------------------------------------------------------
    //  Request shapes (kept here to avoid yet another DTO file)
    // ---------------------------------------------------------------

    public static class StartRequest {
        public String title;
        public String category;
        public String thumbnailUrl;
        public String privacy;
    }
}
