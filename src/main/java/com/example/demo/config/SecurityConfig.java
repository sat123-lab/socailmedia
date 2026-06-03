package com.example.demo.config;

import com.example.demo.repository.UserRepository;
import com.example.demo.security.AdminAccessGuard;
import com.example.demo.security.JwtFilter;
import com.example.demo.security.RateLimitFilter;
import com.example.demo.security.SecureHeadersFilter;
import com.example.demo.security.SecurityProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Hardened security filter chain.
 *
 * <h3>Filter order (outer → inner)</h3>
 * <ol>
 *   <li>{@link SecureHeadersFilter} — sets HSTS, CSP, XCTO, XFO on every response.</li>
 *   <li>{@link RateLimitFilter} — per-IP, per-route-group throttle (429 on overflow).</li>
 *   <li>Spring CORS layer — restricted to configured origins.</li>
 *   <li>{@link JwtFilter} — parses Bearer token, sets Authentication + role authorities.</li>
 * </ol>
 *
 * <p>Session policy is STATELESS — there are no server-side HTTP sessions; every request
 * carries its own JWT.</p>
 */
@Configuration
@RequiredArgsConstructor
@EnableWebSecurity
public class SecurityConfig {

    private final JwtFilter jwtFilter;
    private final SecureHeadersFilter secureHeadersFilter;
    private final RateLimitFilter rateLimitFilter;
    private final AdminAccessGuard adminAccessGuard;
    private final SecurityProperties securityProperties;
    private final UserRepository userRepository;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(httpBasic -> httpBasic.disable())
                .formLogin(formLogin -> formLogin.disable())
                .authorizeHttpRequests(auth -> auth
                        // CORS preflights must always be allowed — browsers send
                        // them without an Authorization header.
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()

                        // Public endpoints
                        .requestMatchers(
                                "/api/auth",
                                "/api/auth/**",
                                "/auth/**",
                                "/security/refresh",
                                "/upload/**",
                                "/uploads/**",
                                "/posts/feed",
                                "/posts/{postId}",
                                "/posts/user/*/count",
                                "/ads/active",
                                "/ws/**",
                                "/ws-native/**",
                                "/media/serve/**",
                                // Swagger UI and API docs
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/swagger-ui.html",
                                "/webjars/swagger-ui/**",
                                // Actuator health endpoints
                                "/actuator/health/**",
                                "/actuator/health",
                                "/actuator/info",
                                // Anonymous referral landing endpoints — clicks
                                // and code resolution are intentionally public so
                                // marketing/web-app can hit them pre-auth.
                                "/referrals/clicks",
                                "/referrals/resolve"
                        ).permitAll()

                        // Admin endpoints require ADMIN role
                        .requestMatchers("/admin/**").hasRole("ADMIN")

                        // Everything else requires authentication
                        .anyRequest().authenticated()
                )
                // Return 401 (not 403) when no/invalid token is provided. This is
                // the conventional signal for clients to refresh and retry.
                .exceptionHandling(eh -> eh.authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(401);
                    res.setContentType("application/json");
                    res.getWriter().write("{\"error\":\"Authentication required\"}");
                }))
                // Outermost wrap: secure headers + rate limit BEFORE the JWT filter so
                // unauthenticated abuse still hits the rate limiter.
                .addFilterBefore(secureHeadersFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                // AdminAccessGuard runs AFTER JWT so the SecurityContext is populated.
                .addFilterAfter(adminAccessGuard, JwtFilter.class);

        return http.build();
    }

    @Bean
    public UserDetailsService userDetailsService() {
        return username -> userRepository.findByEmail(
                        username == null ? "" : username.trim().toLowerCase())
                .map(user -> org.springframework.security.core.userdetails.User.builder()
                        .username(user.getEmail())
                        .password(user.getPassword() == null ? "" : user.getPassword())
                        .roles(user.isAdmin() ? "ADMIN" : "USER")
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig)
            throws Exception {
        return authConfig.getAuthenticationManager();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        String origins = securityProperties.getCors().getAllowedOrigins();
        if (origins == null || origins.isBlank() || "*".equals(origins.trim())) {
            // Dev fallback — allow any origin but DO NOT include credentials.
            config.setAllowedOriginPatterns(List.of("*"));
            config.setAllowCredentials(false);
        } else {
            config.setAllowedOrigins(Arrays.asList(origins.split("\\s*,\\s*")));
            config.setAllowCredentials(true);
        }
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(
                "X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After"));
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource src = new UrlBasedCorsConfigurationSource();
        src.registerCorsConfiguration("/**", config);
        return src;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with default cost (10) — easily tuned upward as hardware improves.
        return new BCryptPasswordEncoder();
    }
}
