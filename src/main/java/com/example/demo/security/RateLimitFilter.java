package com.example.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Lightweight in-memory token-bucket rate limiter — per IP, per route group.
 *
 * <p>For multi-instance deployments swap the {@link ConcurrentHashMap} for Redis
 * (Bucket4j has a Redis adapter — drop in without changing this class).</p>
 *
 * <h3>Route groups</h3>
 * <ul>
 *   <li>{@code /auth/**} — auth (login, OTP, register) — {@code auth-rpm}</li>
 *   <li>{@code /upload/**} — file upload — {@code upload-rpm}</li>
 *   <li>everything else — {@code api-rpm}</li>
 * </ul>
 *
 * <p>Status {@code 429 Too Many Requests} with a {@code Retry-After} header is returned
 * once the bucket is empty for the current minute.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RateLimitFilter extends OncePerRequestFilter {

    private final SecurityProperties properties;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        String ip = clientIp(req);
        String group = routeGroup(req.getRequestURI());
        int limit = limitFor(group);
        String key = ip + ":" + group + ":" + currentMinute();

        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(limit));
        int remaining = bucket.tryConsume();

        if (remaining < 0) {
            res.setStatus(429);
            res.setHeader("Retry-After", "60");
            res.setHeader("X-RateLimit-Limit", Integer.toString(limit));
            res.setHeader("X-RateLimit-Remaining", "0");
            res.setContentType("application/json");
            res.getWriter().write("{\"error\":\"Too many requests. Slow down and try again.\"}");
            return;
        }
        res.setHeader("X-RateLimit-Limit", Integer.toString(limit));
        res.setHeader("X-RateLimit-Remaining", Integer.toString(remaining));

        // Garbage-collect old minute keys (cheap probabilistic)
        if (buckets.size() > 10_000) evictOldBuckets();

        chain.doFilter(req, res);
    }

    private int limitFor(String group) {
        return switch (group) {
            case "auth" -> properties.getRateLimit().getAuthRpm();
            case "upload" -> properties.getRateLimit().getUploadRpm();
            default -> properties.getRateLimit().getApiRpm();
        };
    }

    private static String routeGroup(String uri) {
        if (uri == null) return "api";
        if (uri.startsWith("/auth")) return "auth";
        if (uri.startsWith("/upload")) return "upload";
        return "api";
    }

    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return req.getRemoteAddr();
    }

    private static long currentMinute() {
        return System.currentTimeMillis() / 60_000L;
    }

    private void evictOldBuckets() {
        long now = currentMinute();
        buckets.keySet().removeIf(k -> {
            int last = k.lastIndexOf(':');
            try {
                long minute = Long.parseLong(k.substring(last + 1));
                return minute < now - 2; // keep current + previous minute
            } catch (NumberFormatException e) {
                return true;
            }
        });
    }

    private static final class Bucket {
        private final int limit;
        private final AtomicInteger used = new AtomicInteger(0);

        Bucket(int limit) { this.limit = limit; }

        int tryConsume() {
            int current = used.incrementAndGet();
            if (current > limit) {
                used.decrementAndGet();
                return -1;
            }
            return limit - current;
        }
    }
}
