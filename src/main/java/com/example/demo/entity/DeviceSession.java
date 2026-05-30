package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * One row per active login. Created when a refresh token is issued; deleted
 * (or marked revoked) when the user logs out from the corresponding device.
 *
 * <p>Linked back to {@link RefreshToken} via {@code refreshFamilyId} so that
 * revoking the session also kills the refresh-token family.</p>
 */
@Entity
@Table(name = "device_sessions", indexes = {
        @Index(name = "idx_session_user",   columnList = "userId"),
        @Index(name = "idx_session_family", columnList = "refreshFamilyId", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    /** Same id used in {@link RefreshToken#familyId}. */
    @Column(nullable = false, unique = true, length = 64)
    private String refreshFamilyId;

    @Column(length = 64)
    private String deviceFingerprint;

    @Column(length = 64)
    private String platform;

    @Column(length = 128)
    private String deviceName;

    @Column(length = 256)
    private String userAgent;

    @Column(length = 48)
    private String ipAddress;

    /** Approximate "City, Country" — populated by a future geo-lookup integration. */
    @Column(length = 96)
    private String location;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(nullable = false)
    private boolean revoked;

    private LocalDateTime revokedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (lastSeenAt == null) lastSeenAt = now;
    }
}
