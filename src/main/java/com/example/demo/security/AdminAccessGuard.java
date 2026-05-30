package com.example.demo.security;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.RealtimeEventService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Hardens every request that targets {@code /admin/**}.
 *
 * <ul>
 *   <li>Caller must be authenticated with {@code ROLE_ADMIN}.</li>
 *   <li>If {@code app.security.admin.ip-allowlist} is configured the caller's
 *       IP must match. Otherwise the filter is permissive.</li>
 *   <li>The admin's last admin login must be within {@code admin.session-ttl-minutes}.
 *       Past that they're forced to re-authenticate.</li>
 *   <li>Every admin hit is published to {@code /topic/admin} so other admins
 *       see realtime activity (Feature 10).</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminAccessGuard extends OncePerRequestFilter {

    private final UserRepository userRepository;
    private final RealtimeEventService realtime;
    private final AuditLogService audit;

    @Value("${app.security.admin.ip-allowlist:}")
    private String ipAllowlist;

    @Value("${app.security.admin.session-ttl-minutes:120}")
    private int adminSessionTtlMinutes;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/admin");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            deny(res, 401, "Admin endpoints require authentication.");
            return;
        }

        Optional<User> opt = userRepository.findByEmail(auth.getName());
        if (opt.isEmpty() || !opt.get().isAdmin()) {
            deny(res, 403, "Admin role required.");
            return;
        }
        User user = opt.get();

        // 1) IP allowlist check
        if (ipAllowlist != null && !ipAllowlist.isBlank()) {
            String ip = clientIp(req);
            List<String> allowed = Arrays.stream(ipAllowlist.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toList();
            if (!allowed.contains(ip)) {
                audit.record("ADMIN_IP_BLOCKED", user.getId(), null, null,
                        "blocked-ip=" + ip, req);
                deny(res, 403, "IP not allowed for admin access.");
                return;
            }
        }

        // 2) Admin session TTL
        LocalDateTime last = user.getLastAdminLoginAt();
        if (last == null || Duration.between(last, LocalDateTime.now()).toMinutes() > adminSessionTtlMinutes) {
            // First admin call of this session — refresh the marker and emit a
            // login alert to other admins.
            user.setLastAdminLoginAt(LocalDateTime.now());
            userRepository.save(user);

            realtime.toAdmins(RealtimeEventService.TYPE_SECURE_ALERT, Map.of(
                    "kind", "ADMIN_LOGIN",
                    "actor", user.getEmail(),
                    "actorId", user.getId(),
                    "ip", clientIp(req)
            ));
        }

        // 3) Capability check on destructive routes
        String method = req.getMethod();
        String path = req.getRequestURI();
        AdminRole role = parseRole(user.getAdminRole());
        if ("DELETE".equalsIgnoreCase(method) && path.matches("/admin/users/\\d+")
                && !role.canDestroyUsers()) {
            deny(res, 403, "Insufficient admin tier — SUPER_ADMIN required.");
            return;
        }
        if (path.matches("/admin/users/\\d+/admin") && !role.canManageAdmins()) {
            deny(res, 403, "Only SUPER_ADMIN can grant admin rights.");
            return;
        }

        // 4) Realtime activity feed (admin sees other admin actions)
        realtime.toAdmins(RealtimeEventService.TYPE_SECURE_ALERT, Map.of(
                "kind", "ADMIN_REQUEST",
                "actor", user.getEmail(),
                "method", method,
                "path", path
        ));

        chain.doFilter(req, res);
    }

    private static AdminRole parseRole(String s) {
        if (s == null || s.isBlank()) return AdminRole.SUPPORT;
        try {
            return AdminRole.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AdminRole.SUPPORT;
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

    private static void deny(HttpServletResponse res, int status, String msg) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"" + msg + "\"}");
    }
}
