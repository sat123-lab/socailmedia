package com.example.demo.security;

import com.example.demo.entity.DeviceSession;
import com.example.demo.entity.RefreshToken;
import com.example.demo.entity.User;
import com.example.demo.repository.DeviceSessionRepository;
import com.example.demo.repository.RefreshTokenRepository;
import com.example.demo.repository.UserRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues, validates and revokes JWT access + refresh tokens.
 *
 * <h2>Access tokens</h2>
 * Short-lived (default 15 min), HS512, JWT identifier (jti) embedded so the client can be
 * reasoned about server-side if needed. Validated on every protected request by
 * {@link JwtFilter}.
 *
 * <h2>Refresh tokens</h2>
 * Long-lived (default 30 days). Stored in DB ({@link RefreshToken}) so revocation is real —
 * deleting a row invalidates the token immediately. A rotating "family" id catches token
 * reuse attacks: when a refresh token is used twice, the entire family is killed.
 *
 * <h2>Why HS512 (not HS256)</h2>
 * Larger key size & wider output protects against brute-force of the signing key. Matches
 * Signal / Telegram. Tokens are slightly larger but stay under typical header limits.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TokenManager {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_TYPE = "typ";
    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_REFRESH = "refresh";

    private final SecurityProperties properties;
    private final RefreshTokenRepository refreshTokenRepository;
    private final DeviceSessionRepository deviceSessionRepository;
    private final UserRepository userRepository;

    private final SecureRandom secureRandom = new SecureRandom();

    private Key accessKey;
    private Key refreshKey;

    @PostConstruct
    void init() {
        this.accessKey = loadKey(properties.getJwt().getAccessSecretBase64(), "access");
        this.refreshKey = loadKey(properties.getJwt().getRefreshSecretBase64(), "refresh");
        log.info("TokenManager ready — access TTL {}s, refresh TTL {}s",
                properties.getJwt().getAccessTtlSeconds(),
                properties.getJwt().getRefreshTtlSeconds());
    }

    // ------------------------------------------------------------------
    //  Access token
    // ------------------------------------------------------------------

    public String issueAccessToken(User user) {
        long ttl = properties.getJwt().getAccessTtlSeconds();
        Date now = new Date();
        return Jwts.builder()
                .setSubject(user.getEmail())
                .setId(UUID.randomUUID().toString())
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_ROLE, user.isAdmin() ? "ADMIN" : "USER")
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ttl * 1000))
                .signWith(accessKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public Optional<Claims> parseAccessToken(String token) {
        return parse(token, accessKey, TYPE_ACCESS);
    }

    // ------------------------------------------------------------------
    //  Refresh token
    // ------------------------------------------------------------------

    public String issueRefreshToken(User user, String deviceFingerprint) {
        return issueRefreshToken(user, deviceFingerprint, null, null, null, null);
    }

    public String issueRefreshToken(User user, String deviceFingerprint, String platform,
                                    String deviceName, String userAgent, String ip) {
        String familyId = UUID.randomUUID().toString();
        String token = issueRefreshTokenInFamily(user, familyId, deviceFingerprint);

        // Register a DeviceSession row so the user can list / revoke from the UI.
        try {
            deviceSessionRepository.save(DeviceSession.builder()
                    .userId(user.getId())
                    .refreshFamilyId(familyId)
                    .deviceFingerprint(deviceFingerprint)
                    .platform(platform)
                    .deviceName(deviceName)
                    .userAgent(userAgent)
                    .ipAddress(ip)
                    .revoked(false)
                    .build());
        } catch (Exception e) {
            log.warn("Failed to register device session: {}", e.getMessage());
        }
        return token;
    }

    /**
     * Rotates a refresh token within the same family — atomically revokes the previous one.
     * If the previous token had already been revoked, the entire family is killed
     * (signals a stolen-token replay).
     */
    public String rotateRefreshToken(RefreshToken previous, User user) {
        if (previous.isRevoked()) {
            int killed = refreshTokenRepository.revokeFamily(previous.getFamilyId(), LocalDateTime.now());
            deviceSessionRepository.revokeAllForUser(previous.getUserId(), LocalDateTime.now());
            log.warn("Refresh-token reuse detected. Killed family {} ({} tokens).", previous.getFamilyId(), killed);
            throw new SecurityException("Refresh token reuse detected.");
        }
        previous.setRevoked(true);
        previous.setRevokedAt(LocalDateTime.now());
        refreshTokenRepository.save(previous);
        return issueRefreshTokenInFamily(user, previous.getFamilyId(), previous.getDeviceFingerprint());
    }

    private String issueRefreshTokenInFamily(User user, String familyId, String deviceFingerprint) {
        long ttl = properties.getJwt().getRefreshTtlSeconds();
        String jti = UUID.randomUUID().toString();
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttl * 1000);

        // Persist BEFORE returning so a crash mid-issue cannot give the client a token we
        // forgot to remember.
        RefreshToken stored = RefreshToken.builder()
                .jti(jti)
                .familyId(familyId)
                .userId(user.getId())
                .deviceFingerprint(deviceFingerprint)
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.ofInstant(exp.toInstant(), java.time.ZoneId.systemDefault()))
                .revoked(false)
                .build();
        refreshTokenRepository.save(stored);

        return Jwts.builder()
                .setSubject(user.getEmail())
                .setId(jti)
                .claim(CLAIM_USER_ID, user.getId())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .claim("fam", familyId)
                .setIssuedAt(now)
                .setExpiration(exp)
                .signWith(refreshKey, SignatureAlgorithm.HS512)
                .compact();
    }

    public Optional<RefreshToken> validateRefreshToken(String token) {
        Optional<Claims> claims = parse(token, refreshKey, TYPE_REFRESH);
        if (claims.isEmpty()) return Optional.empty();

        String jti = claims.get().getId();
        Optional<RefreshToken> row = refreshTokenRepository.findByJti(jti);
        if (row.isEmpty()) {
            log.warn("Refresh JWT signed correctly but no DB row for jti={} — possible offline forgery", jti);
            return Optional.empty();
        }
        RefreshToken t = row.get();
        if (t.isRevoked() || t.getExpiresAt().isBefore(LocalDateTime.now())) {
            return Optional.empty();
        }
        return Optional.of(t);
    }

    public void revokeAllForUser(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.revokeAllForUser(userId, now);
        deviceSessionRepository.revokeAllForUser(userId, now);
    }

    /** Revoke a single session by its DeviceSession id. Kills the refresh family too. */
    public void revokeSession(DeviceSession session) {
        if (session == null) return;
        LocalDateTime now = LocalDateTime.now();
        refreshTokenRepository.revokeFamily(session.getRefreshFamilyId(), now);
        session.setRevoked(true);
        session.setRevokedAt(now);
        deviceSessionRepository.save(session);
    }

    // ------------------------------------------------------------------
    //  Internals
    // ------------------------------------------------------------------

    private Key loadKey(String b64, String label) {
        if (b64 == null || b64.isBlank()) {
            throw new IllegalStateException("app.security.jwt." + label + "-secret-base64 missing");
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(b64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("app.security.jwt." + label + "-secret-base64 not valid base64", e);
        }
        if (raw.length < 64) {
            log.warn("{} JWT key is {} bytes (<64); stretching with SHA-512.", label, raw.length);
            raw = sha512(raw);
        }
        return Keys.hmacShaKeyFor(raw);
    }

    private Optional<Claims> parse(String token, Key key, String expectedType) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            String type = (String) claims.get(CLAIM_TYPE);
            if (!expectedType.equals(type)) return Optional.empty();
            if (claims.getExpiration() != null && claims.getExpiration().toInstant().isBefore(Instant.now())) {
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static byte[] sha512(byte[] input) {
        try {
            return java.security.MessageDigest.getInstance("SHA-512").digest(input);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Look up a {@link User} by the email subject of a parsed access token. */
    public Optional<User> userForClaims(Claims claims) {
        if (claims == null) return Optional.empty();
        return userRepository.findByEmail(claims.getSubject());
    }
}
