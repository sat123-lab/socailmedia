package com.example.demo.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Sets the canonical security headers on every HTTP response.
 *
 * <ul>
 *   <li>{@code Strict-Transport-Security} — force HTTPS once HTTPS is reachable.</li>
 *   <li>{@code Content-Security-Policy} — restricts script/style/media sources.</li>
 *   <li>{@code X-Content-Type-Options: nosniff} — MIME hardening.</li>
 *   <li>{@code X-Frame-Options: DENY} — kills clickjacking.</li>
 *   <li>{@code Referrer-Policy} — strips outgoing referrer leakage.</li>
 *   <li>{@code Permissions-Policy} — disables every browser API we don't use.</li>
 *   <li>{@code Cross-Origin-Opener-Policy / Embedder-Policy} — isolates the origin.</li>
 * </ul>
 */
@Component
public class SecureHeadersFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        res.setHeader("Strict-Transport-Security", "max-age=63072000; includeSubDomains; preload");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        res.setHeader("Cross-Origin-Resource-Policy", "same-site");
        res.setHeader("Permissions-Policy",
                "geolocation=(), camera=(), microphone=(self), payment=(), usb=(), midi=()");
        res.setHeader("Content-Security-Policy",
                "default-src 'self'; " +
                        "script-src 'self' 'unsafe-inline'; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "img-src 'self' data: blob: https:; " +
                        "media-src 'self' blob:; " +
                        "connect-src 'self' wss: https:; " +
                        "frame-ancestors 'none';");

        chain.doFilter(req, res);
    }
}
