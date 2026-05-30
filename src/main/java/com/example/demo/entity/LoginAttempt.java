package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Rolling counter used by {@link com.example.demo.security.BruteForceProtectionService}
 * to lock an identity out after too many failed login attempts.
 */
@Entity
@Table(name = "login_attempts", indexes = {
        @Index(name = "idx_login_id", columnList = "identifier", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Email or phone — whatever the user typed at login. */
    @Column(nullable = false, unique = true, length = 191)
    private String identifier;

    @Column(nullable = false)
    private int failureCount;

    /** When the counter started (used to expire the window). */
    @Column(nullable = false)
    private LocalDateTime firstFailureAt;

    /** Lock until this time. */
    private LocalDateTime lockedUntil;
}
