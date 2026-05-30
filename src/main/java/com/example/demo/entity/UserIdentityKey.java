package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Long-lived public identity key used for end-to-end encryption.
 *
 * <p>This is the user's Curve25519 (X25519) public key — the corresponding private key
 * NEVER leaves the device. The server only holds the public half.</p>
 *
 * <p>When a sender wants to start a conversation with this user it fetches the identity
 * key plus one one-time pre-key, runs ECDH to derive a fresh AES-256-GCM key, encrypts
 * the message and uploads the opaque envelope. The recipient device can derive the same
 * symmetric key, decrypt locally, and the server never sees plaintext.</p>
 */
@Entity
@Table(name = "user_identity_keys", indexes = {
        @Index(name = "idx_identity_user", columnList = "userId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserIdentityKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long userId;

    /** X25519 public key (32 bytes), base64 encoded. */
    @Column(nullable = false, length = 88)
    private String identityPublicKeyBase64;

    /** Ed25519 signing public key (32 bytes), base64. Used to sign pre-keys. */
    @Column(nullable = false, length = 88)
    private String signingPublicKeyBase64;

    /**
     * Algorithm tag — lets clients negotiate future formats.
     *
     * <p>Widened from 16 → 64 chars in V7. The original 16-char limit truncated
     * the default {@code "X25519-Ed25519-AESGCM"} (21 chars) and any future
     * versioned tags like {@code "X25519-Ed25519-AESGCM-V2"}.</p>
     */
    @Column(nullable = false, length = 64)
    private String algorithm;

    /** Monotonic counter — incremented whenever the user re-generates their keys. */
    @Column(nullable = false)
    private int keyVersion;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (algorithm == null) algorithm = "X25519-Ed25519-AESGCM";
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
