package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Maps a user to one or more FCM registration tokens (one per device).
 *
 * <p>Marked invalid (instead of deleted) when FCM responds {@code
 * NOT_REGISTERED} or {@code INVALID_ARGUMENT}; that way we can purge in
 * batches via the scheduled cleanup.</p>
 */
@Entity
@Table(name = "fcm_tokens",
       uniqueConstraints = @UniqueConstraint(name = "uq_fcm_token", columnNames = "token"),
       indexes = @Index(name = "idx_fcm_user", columnList = "userId, invalid"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class FcmToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 512)
    private String token;

    @Column(length = 16)
    private String platform;   // android / ios / web

    @Column(length = 128)
    private String deviceName;

    @Column(length = 16)
    private String locale;

    @Column(length = 32)
    private String appVersion;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime lastSeenAt;

    @Builder.Default
    @Column(nullable = false)
    private boolean invalid = false;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() { updatedAt = LocalDateTime.now(); }
}
