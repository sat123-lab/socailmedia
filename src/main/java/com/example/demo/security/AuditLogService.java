package com.example.demo.security;

import com.example.demo.entity.AuditLogEntry;
import com.example.demo.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Thin façade over {@link AuditLogRepository} that records security-relevant events.
 *
 * <p>All methods are fire-and-forget — they MUST NOT throw because they sit alongside
 * critical security paths (failed logins, admin actions). Any persistence problem is
 * logged but swallowed so the calling code keeps working.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditLogService {

    private final AuditLogRepository repository;
    private final SecurityProperties properties;

    // ---- action constants ----
    public static final String LOGIN_SUCCESS = "LOGIN_SUCCESS";
    public static final String LOGIN_FAILURE = "LOGIN_FAILURE";
    public static final String LOGIN_BLOCKED = "LOGIN_BLOCKED";
    public static final String TOKEN_REFRESH = "TOKEN_REFRESH";
    public static final String TOKEN_REUSE = "TOKEN_REUSE_DETECTED";
    public static final String LOGOUT = "LOGOUT";
    public static final String ADMIN_ACTION = "ADMIN_ACTION";
    public static final String KEY_BUNDLE_UPLOAD = "KEY_BUNDLE_UPLOAD";
    public static final String ENCRYPTION_TOGGLED = "ENCRYPTION_TOGGLED";
    public static final String ROOM_TOKEN_ISSUED = "ROOM_TOKEN_ISSUED";

    public void record(String action, Long actorUserId, Long targetId,
                       String targetType, String metadata, HttpServletRequest request) {
        try {
            AuditLogEntry entry = AuditLogEntry.builder()
                    .action(action)
                    .actorUserId(actorUserId)
                    .targetId(targetId)
                    .targetType(targetType)
                    .metadata(truncate(metadata, 1024))
                    .remoteIp(request == null ? null : clientIp(request))
                    .userAgent(request == null ? null : truncate(request.getHeader("User-Agent"), 256))
                    .createdAt(LocalDateTime.now())
                    .build();
            repository.save(entry);
        } catch (Exception e) {
            log.warn("Audit log write failed for action {}: {}", action, e.getMessage());
        }
    }

    public void record(String action, Long actorUserId, String metadata) {
        record(action, actorUserId, null, null, metadata, null);
    }

    /** Runs daily — drops audit rows older than the configured retention. */
    @Scheduled(cron = "0 30 3 * * *")
    public void purgeOldEntries() {
        try {
            int days = properties.getAudit().getRetentionDays();
            LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
            int deleted = repository.purgeOlderThan(cutoff);
            if (deleted > 0) {
                log.info("Audit purge: removed {} entries older than {} days.", deleted, days);
            }
        } catch (Exception e) {
            log.warn("Audit purge failed: {}", e.getMessage());
        }
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
