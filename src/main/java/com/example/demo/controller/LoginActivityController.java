package com.example.demo.controller;

import com.example.demo.entity.AuditLogEntry;
import com.example.demo.entity.User;
import com.example.demo.repository.AuditLogRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

/**
 * Surfaces audit-log rows for the calling user — login successes / failures,
 * OTP logins, Google logins, refresh-token activity, session revocations,
 * key-bundle uploads.
 *
 * <p>This is the equivalent of Instagram / WhatsApp's "Login activity" page.
 * Every event is timestamped and includes the originating IP + user agent so
 * users can spot a suspicious session and revoke it from the Sessions screen.</p>
 */
@RestController
@RequestMapping("/security/activity")
@RequiredArgsConstructor
public class LoginActivityController {

    private static final Set<String> AUTH_ACTIONS = Set.of(
            "LOGIN_SUCCESS", "LOGIN_FAILURE", "LOGIN_BLOCKED",
            "TOKEN_REFRESH", "TOKEN_REUSE_DETECTED", "LOGOUT",
            "KEY_BUNDLE_UPLOAD", "ENCRYPTION_TOGGLED",
            "SESSION_REVOKE", "SESSION_REVOKE_ALL");

    private final AuditLogRepository auditRepo;
    private final UserRepository userRepository;

    @GetMapping
    public List<AuditLogEntry> activity(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new SecurityException("Not authenticated"));

        // Pull a window of the user's actions; filter to security-relevant ones.
        var pageReq = PageRequest.of(page, Math.min(Math.max(size, 1), 200),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return auditRepo.findByActorUserIdOrderByCreatedAtDesc(user.getId(), pageReq)
                .getContent().stream()
                .filter(e -> e.getAction() != null && AUTH_ACTIONS.contains(e.getAction()))
                .toList();
    }
}
