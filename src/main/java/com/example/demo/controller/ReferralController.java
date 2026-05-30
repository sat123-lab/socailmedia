package com.example.demo.controller;

import com.example.demo.dto.ReferralCodeDto;
import com.example.demo.dto.ReferralListItemDto;
import com.example.demo.dto.ReferralStatsDto;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ReferralService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST surface for the Referral & Invite system.
 *
 * <ul>
 *   <li>{@code GET  /referrals/me}                 — auth — the inviter's share bundle.</li>
 *   <li>{@code GET  /referrals/list?limit=}        — auth — invited-users list.</li>
 *   <li>{@code GET  /referrals/stats}              — auth — dashboard stats + growth.</li>
 *   <li>{@code POST /referrals/clicks}             — public — anonymous click ingestion.</li>
 *   <li>{@code GET  /referrals/resolve?code=}      — public — code → inviter chrome
 *       used by the landing page.</li>
 * </ul>
 *
 * <p>Public endpoints intentionally don't require auth so the
 * marketing/web-app can hit them from the referral landing route.
 * Add them to {@code SecurityConfig.permitAll(...)} if {@code anyRequest().authenticated()}
 * is the default.
 */
@RestController
@RequestMapping("/referrals")
@RequiredArgsConstructor
public class ReferralController {

    private final ReferralService referrals;
    private final UserRepository users;

    // ========================================================================
    //  Authenticated endpoints
    // ========================================================================

    @GetMapping("/me")
    public ResponseEntity<ReferralCodeDto> me(Authentication auth) {
        User u = currentUser(auth);
        if (u == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(referrals.me(u));
    }

    @GetMapping("/list")
    public ResponseEntity<List<ReferralListItemDto>> list(
            @RequestParam(defaultValue = "50") int limit, Authentication auth) {
        User u = currentUser(auth);
        if (u == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(referrals.list(u.getId(), limit));
    }

    @GetMapping("/stats")
    public ResponseEntity<ReferralStatsDto> stats(Authentication auth) {
        User u = currentUser(auth);
        if (u == null) return ResponseEntity.status(401).build();
        return ResponseEntity.ok(referrals.stats(u.getId()));
    }

    // ========================================================================
    //  Public endpoints
    // ========================================================================

    /**
     * Anonymous landing-page click. Body is intentionally tiny so the
     * marketing site can fire-and-forget without a JSON parser.
     */
    @PostMapping("/clicks")
    public ResponseEntity<Map<String, Object>> click(
            @RequestBody ClickBody body,
            HttpServletRequest req) {
        if (body == null || body.code == null || body.code.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("ok", false));
        }
        referrals.recordClick(
                body.code,
                resolveClientIp(req),
                body.deviceId,
                req.getHeader("User-Agent"),
                body.source);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/resolve")
    public ResponseEntity<Map<String, Object>> resolve(@RequestParam String code) {
        try {
            return ResponseEntity.ok(referrals.resolvePublic(code));
        } catch (jakarta.persistence.EntityNotFoundException e) {
            return ResponseEntity.status(404)
                    .body(Map.of("error", "unknown_code"));
        }
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    private User currentUser(Authentication auth) {
        if (auth == null) return null;
        return users.findByEmail(auth.getName()).orElse(null);
    }

    /**
     * Cloud-aware IP resolution — checks X-Forwarded-For when behind a
     * load balancer / CDN, falls back to the remote addr otherwise. We
     * never trust XFF blindly: the value is hashed, so even a spoofed
     * header just creates a noise hash.
     */
    private static String resolveClientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma < 0 ? xff : xff.substring(0, comma)).trim();
        }
        return req.getRemoteAddr();
    }

    /** Tiny inbound shape — kept here to avoid spawning a one-field DTO. */
    public static class ClickBody {
        public String code;
        public String deviceId;
        public String source;
    }
}
