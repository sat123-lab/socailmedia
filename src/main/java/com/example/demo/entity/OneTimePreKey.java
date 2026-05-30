package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One-time pre-key (X25519) — burned the first time a sender asks for it.
 *
 * <p>Used to give each new conversation a fresh shared secret without requiring the
 * recipient to be online. The client uploads a few hundred of these on first key
 * generation and tops up the pool periodically.</p>
 *
 * <p>After the {@code used} flag is set the key is never handed out again.</p>
 */
@Entity
@Table(name = "one_time_prekeys", indexes = {
        @Index(name = "idx_prekey_user_used", columnList = "userId,used"),
        @Index(name = "idx_prekey_key_id", columnList = "keyId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OneTimePreKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** Stable client-generated id so the client can correlate which key was consumed. */
    @Column(nullable = false, length = 36)
    private String keyId;

    /** Public half of the one-time key (32 bytes), base64. */
    @Column(nullable = false, length = 88)
    private String publicKeyBase64;

    /** Ed25519 signature by the identity key (64 bytes), base64. */
    @Column(length = 128)
    private String signatureBase64;

    @Column(nullable = false)
    private boolean used;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime consumedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
