package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * A temporary ban on either a user id or an IP address.
 *
 * <p>Created by {@link com.example.demo.security.SecurityMonitorService} when
 * suspicious activity (rapid login failures, room spam, token reuse) crosses
 * a threshold. The {@code RateLimitFilter} + auth flow consult this table
 * before letting traffic through.</p>
 */
@Entity
@Table(name = "temp_bans", indexes = {
        @Index(name = "idx_ban_subject", columnList = "subject"),
        @Index(name = "idx_ban_expires", columnList = "expiresAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TempBan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** "USER:42" or "IP:203.0.113.10". */
    @Column(nullable = false, length = 96)
    private String subject;

    @Column(length = 128)
    private String reason;

    @Column(nullable = false)
    private LocalDateTime issuedAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;
}
