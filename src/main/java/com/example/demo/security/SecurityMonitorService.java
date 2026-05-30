package com.example.demo.security;

import com.example.demo.entity.TempBan;
import com.example.demo.repository.TempBanRepository;
import com.example.demo.service.RealtimeEventService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Realtime anomaly detector + temporary-ban enforcer.
 *
 * <p>Detects:</p>
 * <ul>
 *   <li>Rapid login failures from a single IP (vertical brute force)</li>
 *   <li>Refresh token reuse</li>
 *   <li>Excessive room join attempts</li>
 *   <li>Unusual login patterns (multiple geographies in short window — left as
 *       an integration point for a future geo-lookup service)</li>
 * </ul>
 *
 * <p>When a counter crosses its threshold the service:</p>
 * <ol>
 *   <li>Writes a {@link TempBan} row that affects the offending subject for N minutes.</li>
 *   <li>Pushes a {@code secure.alert} event to {@code /topic/admin}.</li>
 *   <li>Optionally notifies the affected user (token reuse case).</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecurityMonitorService {

    private final TempBanRepository banRepository;
    private final RealtimeEventService realtime;
    private final AuditLogService audit;

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();

    private static final int FAIL_LOGIN_THRESHOLD = 5;
    private static final int FAIL_LOGIN_WINDOW_MS = 60_000;
    private static final int FAIL_LOGIN_BAN_MINUTES = 15;

    private static final int ROOM_SPAM_THRESHOLD = 10;
    private static final int ROOM_SPAM_WINDOW_MS = 30_000;
    private static final int ROOM_SPAM_BAN_MINUTES = 30;

    public boolean isBanned(String subject) {
        return banRepository
                .findFirstBySubjectAndExpiresAtAfterOrderByExpiresAtDesc(subject, LocalDateTime.now())
                .isPresent();
    }

    // ============================================================
    //  Event sinks
    // ============================================================

    public void onLoginFailure(String ip) {
        if (ip == null) return;
        Counter c = bump("login-fail:" + ip, FAIL_LOGIN_WINDOW_MS);
        if (c.value() >= FAIL_LOGIN_THRESHOLD) {
            issueBan("IP:" + ip, "Repeated login failures", FAIL_LOGIN_BAN_MINUTES);
            counters.remove("login-fail:" + ip);
        }
    }

    public void onTokenReuse(Long userId) {
        if (userId == null) return;
        issueBan("USER:" + userId, "Refresh-token reuse detected", 60);
        realtime.toUser(userId, RealtimeEventService.TYPE_SECURE_ALERT, Map.of(
                "kind", "TOKEN_REUSE",
                "message", "Your account was signed out everywhere for security."
        ));
    }

    public void onRoomJoinAttempt(Long userId) {
        if (userId == null) return;
        Counter c = bump("room-join:" + userId, ROOM_SPAM_WINDOW_MS);
        if (c.value() >= ROOM_SPAM_THRESHOLD) {
            issueBan("USER:" + userId, "Room join spam", ROOM_SPAM_BAN_MINUTES);
            counters.remove("room-join:" + userId);
        }
    }

    // ============================================================
    //  Ban issuance
    // ============================================================

    public TempBan issueBan(String subject, String reason, int minutes) {
        TempBan ban = TempBan.builder()
                .subject(subject)
                .reason(reason)
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusMinutes(minutes))
                .build();
        banRepository.save(ban);
        log.warn("Temp ban issued: {} for {} ({} min)", subject, reason, minutes);

        Map<String, Object> payload = Map.of(
                "subject", subject,
                "reason", reason,
                "minutes", minutes
        );
        realtime.toAdmins(RealtimeEventService.TYPE_TEMP_BAN, payload);
        audit.record("TEMP_BAN_ISSUED", null, ban.getId(), "TempBan",
                subject + " · " + reason, null);
        return ban;
    }

    @Scheduled(fixedDelay = 60_000L)
    void purgeExpiredBans() {
        try {
            int n = banRepository.purgeExpired(LocalDateTime.now());
            if (n > 0) log.info("Cleared {} expired bans.", n);
        } catch (Exception e) {
            log.warn("TempBan purge failed: {}", e.getMessage());
        }
    }

    // ============================================================
    //  Tiny rolling counter
    // ============================================================

    private Counter bump(String key, int windowMs) {
        return counters.compute(key, (k, prev) -> {
            long now = System.currentTimeMillis();
            if (prev == null || now - prev.firstSeen() > windowMs) {
                return new Counter(now, new AtomicInteger(1));
            }
            prev.counter().incrementAndGet();
            return prev;
        });
    }

    private record Counter(long firstSeen, AtomicInteger counter) {
        int value() { return counter.get(); }
    }
}
