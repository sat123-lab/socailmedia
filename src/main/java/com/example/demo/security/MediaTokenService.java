package com.example.demo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;

/**
 * Signs and validates short-lived tokens that authorise access to a single
 * media file. Tokens carry: {@code path}, {@code userId}, {@code exp}.
 *
 * <p>The token is delivered to clients via {@code GET /media/signed-url} and
 * then used as a query param against {@code /media/serve/**}. Tokens are
 * single-purpose — they cannot be used for any other path and they cannot be
 * used after {@code exp}.</p>
 */
@Service
@RequiredArgsConstructor
public class MediaTokenService {

    public static final long DEFAULT_TTL_SECONDS = 300; // 5 minutes
    public static final String CLAIM_PATH = "p";
    public static final String CLAIM_USER = "u";

    private final SecurityProperties properties;
    private Key key;

    @PostConstruct
    void init() {
        byte[] raw = Base64.getDecoder().decode(properties.getJwt().getRefreshSecretBase64());
        if (raw.length < 64) {
            try { raw = java.security.MessageDigest.getInstance("SHA-512").digest(raw); }
            catch (Exception e) { throw new IllegalStateException(e); }
        }
        this.key = Keys.hmacShaKeyFor(raw);
    }

    public String issueToken(String relativePath, Long userId, Long ttlSeconds) {
        long ttl = (ttlSeconds == null || ttlSeconds <= 0) ? DEFAULT_TTL_SECONDS : ttlSeconds;
        Date now = new Date();
        return Jwts.builder()
                .setSubject("media")
                .claim(CLAIM_PATH, relativePath)
                .claim(CLAIM_USER, userId)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ttl * 1000))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    public Optional<Claims> validate(String token, String expectedPath) {
        if (token == null || token.isBlank()) return Optional.empty();
        try {
            Claims c = Jwts.parserBuilder()
                    .setSigningKey(key).build()
                    .parseClaimsJws(token).getBody();
            String claimedPath = String.valueOf(c.get(CLAIM_PATH));
            if (!claimedPath.equals(expectedPath)) return Optional.empty();
            return Optional.of(c);
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
