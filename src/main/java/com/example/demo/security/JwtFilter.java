package com.example.demo.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates every protected HTTP request.
 *
 * <p>Accepts <em>two</em> token formats:</p>
 * <ol>
 *   <li><b>Access tokens</b> issued by {@link TokenManager} (HS512, with {@code uid} and
 *       {@code role} claims). These set a {@code ROLE_USER} or {@code ROLE_ADMIN} authority
 *       on the {@link org.springframework.security.core.Authentication}.</li>
 *   <li><b>Legacy tokens</b> issued by the original {@link JwtUtil} (HS256). Still
 *       accepted so existing logged-in clients keep working through the upgrade.</li>
 * </ol>
 */
@Component
@RequiredArgsConstructor
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final TokenManager tokenManager;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null) {
            return true;
        }
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return path.startsWith("/api/auth")
                || path.startsWith("/auth")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/webjars/swagger-ui")
                || path.equals("/swagger-ui.html")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        final String jwt = authHeader.substring(7);

        if (SecurityContextHolder.getContext().getAuthentication() == null) {

            // --- 1. Try new TokenManager (HS512 access token) ---
            Optional<Claims> claims = tokenManager.parseAccessToken(jwt);
            if (claims.isPresent()) {
                String role = String.valueOf(claims.get().get(TokenManager.CLAIM_ROLE));
                List<SimpleGrantedAuthority> authorities = "ADMIN".equals(role)
                        ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                                  new SimpleGrantedAuthority("ROLE_USER"))
                        : List.of(new SimpleGrantedAuthority("ROLE_USER"));

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                claims.get().getSubject(), null, authorities);
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);
            } else {
                // --- 2. Fall back to legacy HS256 JwtUtil token ---
                try {
                    String email = jwtUtil.extractEmail(jwt);
                    if (email != null && jwtUtil.validateToken(jwt, email)) {
                        UsernamePasswordAuthenticationToken auth =
                                new UsernamePasswordAuthenticationToken(
                                        email, null, Collections.emptyList());
                        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    }
                } catch (Exception ignored) {
                    // invalid / expired — let downstream return 401
                }
            }
        }

        filterChain.doFilter(request, response);
    }
}
