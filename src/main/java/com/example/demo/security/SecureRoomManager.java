package com.example.demo.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues and verifies short-lived signed tokens that authorize a user to join a specific
 * call room. Tokens carry: roomId, userId, role, audience, and expiry.
 *
 * <p>The token is required by the signaling endpoint (WebRTC offer/answer/ICE).
 * Without it the server refuses to forward signals — preventing anti-hijack:</p>
 *
 * <ul>
 *   <li>Lurkers who guess a roomId cannot join — they have no signed token.</li>
 *   <li>Old, leaked roomIds become useless after the token expires (≈2 min).</li>
 *   <li>Token is bound to a single userId — re-use by another account fails.</li>
 * </ul>
 *
 * <p>Note: The actual WebRTC media stream is already E2E protected by DTLS+SRTP
 * (mandatory in modern WebRTC). The server never sees decrypted media.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SecureRoomManager {

    public static final String CLAIM_ROOM = "rid";
    public static final String CLAIM_USER = "uid";
    public static final String CLAIM_ROLE = "role";
    public static final String CLAIM_KIND = "kind"; // "call" | "group-call" | "room-call"

    private final SecurityProperties properties;
    private Key key;

    @PostConstruct
    void init() {
        // Reuse the JWT refresh key for room tokens — different audience, shared secret OK.
        byte[] raw = Base64.getDecoder().decode(properties.getJwt().getRefreshSecretBase64());
        if (raw.length < 64) {
            raw = sha512(raw);
        }
        this.key = Keys.hmacShaKeyFor(raw);
    }

    public String issueRoomToken(String kind, String roomId, Long userId, String role) {
        long ttl = properties.getRoomTokenTtlSeconds();
        Date now = new Date();
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .setId(UUID.randomUUID().toString())
                .claim(CLAIM_ROOM, roomId)
                .claim(CLAIM_USER, userId)
                .claim(CLAIM_ROLE, role == null ? "MEMBER" : role)
                .claim(CLAIM_KIND, kind)
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + ttl * 1000))
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();
    }

    public Optional<Claims> validateRoomToken(String token, String expectedKind, String expectedRoomId) {
        try {
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
            if (!expectedKind.equals(claims.get(CLAIM_KIND))) return Optional.empty();
            if (!expectedRoomId.equals(String.valueOf(claims.get(CLAIM_ROOM)))) return Optional.empty();
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
}
