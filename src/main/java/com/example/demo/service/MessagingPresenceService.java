package com.example.demo.service;

import com.example.demo.dto.PresenceDto;
import com.example.demo.entity.UserPresence;
import com.example.demo.repository.UserPresenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Online / last-seen / typing / recording indicators.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessagingPresenceService {

    private static final long HEARTBEAT_TIMEOUT_SEC = 90;
    private static final long TYPING_TTL_SEC = 6;

    private final UserPresenceRepository presence;
    private final RealtimeEventService realtime;

    @Transactional
    public PresenceDto heartbeat(Long userId) {
        UserPresence row = presence.findById(userId).orElseGet(() ->
                UserPresence.builder().userId(userId).build());
        row.setOnline(true);
        row.setLastSeenAt(Instant.now());
        presence.save(row);
        broadcast(userId, row);
        return PresenceDto.from(row);
    }

    @Transactional
    public void goOffline(Long userId) {
        presence.findById(userId).ifPresent(row -> {
            row.setOnline(false);
            row.setLastSeenAt(Instant.now());
            row.setTypingInConvId(null);
            row.setTypingUntil(null);
            presence.save(row);
            broadcast(userId, row);
        });
    }

    @Transactional
    public void setTyping(Long userId, Long conversationId, String kind) {
        UserPresence row = presence.findById(userId).orElseGet(() ->
                UserPresence.builder().userId(userId).build());
        row.setOnline(true);
        row.setLastSeenAt(Instant.now());
        row.setTypingInConvId(conversationId);
        row.setTypingKind(kind == null ? "TEXT" : kind);
        row.setTypingUntil(Instant.now().plus(TYPING_TTL_SEC, ChronoUnit.SECONDS));
        presence.save(row);

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("conversationId", conversationId);
        payload.put("kind", row.getTypingKind());
        realtime.toUser(userId, RealtimeEventService.TYPE_TYPING, payload);
    }

    @Transactional
    public void clearTyping(Long userId) {
        presence.findById(userId).ifPresent(row -> {
            row.setTypingInConvId(null);
            row.setTypingUntil(null);
            presence.save(row);
        });
    }

    public PresenceDto get(Long userId, Long viewerId) {
        return presence.findById(userId)
                .map(p -> redact(p, viewerId))
                .orElseGet(() -> {
                    PresenceDto d = new PresenceDto();
                    d.userId = userId;
                    d.online = false;
                    return d;
                });
    }

    public Map<Long, PresenceDto> batch(Collection<Long> userIds, Long viewerId) {
        if (userIds == null || userIds.isEmpty()) return Map.of();
        List<UserPresence> rows = presence.findAllByUserIds(userIds);
        Map<Long, UserPresence> map = rows.stream()
                .collect(Collectors.toMap(UserPresence::getUserId, p -> p));
        Map<Long, PresenceDto> out = new LinkedHashMap<>();
        for (Long id : userIds) {
            UserPresence p = map.get(id);
            out.put(id, p == null ? get(id, viewerId) : redact(p, viewerId));
        }
        return out;
    }

    @Transactional
    public void setPrivacy(Long userId, String privacy) {
        UserPresence row = presence.findById(userId).orElseGet(() ->
                UserPresence.builder().userId(userId).build());
        row.setLastSeenPrivacy(privacy);
        presence.save(row);
    }

    @Scheduled(fixedDelay = 60_000)
    @Transactional
    public void reaperTick() {
        Instant cutoff = Instant.now().minus(HEARTBEAT_TIMEOUT_SEC, ChronoUnit.SECONDS);
        int n = presence.reapStale(cutoff);
        if (n > 0) log.debug("Presence reaper marked {} users offline", n);
    }

    private PresenceDto redact(UserPresence p, Long viewerId) {
        PresenceDto d = PresenceDto.from(p);
        if ("NOBODY".equals(p.getLastSeenPrivacy())) {
            d.lastSeenAt = null;
        }
        // CONTACTS gate would check follow graph — hook for future.
        if (!p.isOnline() && d.lastSeenAt == null) {
            d.online = false;
        }
        return d;
    }

    private void broadcast(Long userId, UserPresence row) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("online", row.isOnline());
        payload.put("lastSeenAt", row.getLastSeenAt().toString());
        realtime.toUser(userId, RealtimeEventService.TYPE_PRESENCE, payload);
    }
}
