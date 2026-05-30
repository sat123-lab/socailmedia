package com.example.demo.controller;

import com.example.demo.entity.DeviceSession;
import com.example.demo.entity.User;
import com.example.demo.repository.DeviceSessionRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.AuditLogService;
import com.example.demo.security.TokenManager;
import com.example.demo.service.RealtimeEventService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Active session management — list, view, revoke individual or all sessions.
 *
 * <p>Endpoints under {@code /security/sessions}:</p>
 * <ul>
 *   <li>{@code GET /security/sessions} — every active device for the caller.</li>
 *   <li>{@code DELETE /security/sessions/{id}} — sign that device out.</li>
 *   <li>{@code DELETE /security/sessions} — sign out everywhere except this device.</li>
 * </ul>
 */
@RestController
@RequestMapping("/security/sessions")
@RequiredArgsConstructor
public class SessionsController {

    private final DeviceSessionRepository sessions;
    private final UserRepository userRepository;
    private final TokenManager tokenManager;
    private final RealtimeEventService realtime;
    private final AuditLogService audit;

    @GetMapping
    public List<DeviceSession> list(Authentication authentication) {
        User user = requireUser(authentication);
        return sessions.findByUserIdAndRevokedFalseOrderByLastSeenAtDesc(user.getId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> revokeOne(@PathVariable Long id,
                                                         Authentication authentication,
                                                         HttpServletRequest http) {
        User user = requireUser(authentication);
        Optional<DeviceSession> opt = sessions.findById(id);
        if (opt.isEmpty() || !opt.get().getUserId().equals(user.getId())) {
            return ResponseEntity.status(404).build();
        }
        DeviceSession s = opt.get();
        tokenManager.revokeSession(s);
        realtime.toUser(user.getId(), RealtimeEventService.TYPE_SESSION_REVOKED,
                Map.of("sessionId", id, "fingerprint", s.getDeviceFingerprint() == null ? "" : s.getDeviceFingerprint()));
        audit.record("SESSION_REVOKE", user.getId(), id, "DeviceSession", "manual", http);
        return ResponseEntity.ok(Map.of("status", "revoked"));
    }

    @DeleteMapping
    public ResponseEntity<Map<String, Object>> revokeAll(Authentication authentication,
                                                         HttpServletRequest http) {
        User user = requireUser(authentication);
        tokenManager.revokeAllForUser(user.getId());
        realtime.toUser(user.getId(), RealtimeEventService.TYPE_SESSION_REVOKED,
                Map.of("scope", "all"));
        audit.record("SESSION_REVOKE_ALL", user.getId(), null, null, "user-initiated", http);
        return ResponseEntity.ok(Map.of("status", "revoked-all"));
    }

    private User requireUser(Authentication a) {
        if (a == null) throw new SecurityException("Not authenticated");
        return userRepository.findByEmail(a.getName())
                .orElseThrow(() -> new SecurityException("User not found"));
    }
}
