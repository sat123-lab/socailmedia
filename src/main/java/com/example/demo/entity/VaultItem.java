package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A single piece of content stored in the user's private vault.
 *
 * <p>The server NEVER sees the plaintext filename, MIME type, or content. The
 * client encrypts everything with a per-user AES-256 vault key (cached only
 * in the device's Keychain) before upload. The columns below store only
 * ciphertext metadata and an opaque blob path.</p>
 */
@Entity
@Table(name = "vault_items", indexes = {
        @Index(name = "idx_vault_owner", columnList = "ownerId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VaultItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long ownerId;

    /** Random handle used as the on-disk blob file name — server-generated. */
    @Column(nullable = false, length = 64, unique = true)
    private String blobId;

    /** Encrypted (base64) original filename. Server cannot read this. */
    @Column(length = 512)
    private String encryptedFilename;

    /** Encrypted (base64) MIME type. */
    @Column(length = 128)
    private String encryptedMimeType;

    /** 12-byte AES-GCM nonce used for {@link #encryptedFilename}, base64. */
    @Column(length = 24)
    private String metadataNonce;

    /** 12-byte AES-GCM nonce used for the blob body, base64. */
    @Column(length = 24, nullable = false)
    private String contentNonce;

    /** Size of the ciphertext on disk in bytes. */
    @Column(nullable = false)
    private long sizeBytes;

    /** "PHOTO", "VIDEO", "NOTE", "FILE" — coarse category only. */
    @Column(length = 16, nullable = false)
    private String kind;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
