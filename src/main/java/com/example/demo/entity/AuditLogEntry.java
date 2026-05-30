package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Append-only audit trail for security-sensitive events.
 *
 * <h2>What gets logged</h2>
 * <ul>
 *   <li>LOGIN_SUCCESS / LOGIN_FAILURE — with truncated user agent &amp; remote IP</li>
 *   <li>TOKEN_REFRESH / TOKEN_REUSE_DETECTED</li>
 *   <li>ADMIN_ACTION — every privileged admin endpoint call (CRUD on users / posts / ads)</li>
 *   <li>KEY_BUNDLE_UPLOADED — E2EE pre-keys rotated</li>
 *   <li>ENCRYPTION_TOGGLED — user changed their own E2EE setting</li>
 * </ul>
 *
 * <p>Plain-text {@code metadata} is allowed because audit entries themselves are never
 * sensitive content — they are about <em>events</em>, not message bodies.</p>
 */
@Entity
@Table(name = "audit_log", indexes = {
        @Index(name = "idx_audit_actor", columnList = "actorUserId"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_at", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String action;

    /** The user who triggered the event, or null for anonymous (e.g. failed login). */
    private Long actorUserId;

    /** Subject of the action — e.g. the deleted user id or post id. */
    private Long targetId;

    @Column(length = 64)
    private String targetType;

    @Column(length = 48)
    private String remoteIp;

    @Column(length = 256)
    private String userAgent;

    @Column(length = 1024)
    private String metadata;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
