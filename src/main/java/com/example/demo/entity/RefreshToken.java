package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Server-side record of an issued refresh JWT. Deleting / revoking this row
 * invalidates the token immediately even though the JWT itself is still
 * cryptographically valid — this is the whole point of having it.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_jti", columnList = "jti", unique = true),
        @Index(name = "idx_refresh_family", columnList = "familyId"),
        @Index(name = "idx_refresh_user", columnList = "userId")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** JWT id — globally unique. */
    @Column(nullable = false, unique = true, length = 64)
    private String jti;

    /** All rotations of the same login share this family id. */
    @Column(nullable = false, length = 64)
    private String familyId;

    @Column(nullable = false)
    private Long userId;

    /** Opaque device hash — empty for web logins. */
    @Column(length = 128)
    private String deviceFingerprint;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    private LocalDateTime revokedAt;
}
