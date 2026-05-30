package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One row per inbound referral funnel event.
 *
 * <p>A new row is created when:
 * <ul>
 *   <li>An anonymous landing-page click is reported by the web/app
 *       ({@link ReferralStatus#PENDING_CLICK}).</li>
 *   <li>A signup is attributed to a code — promoted from a matching
 *       {@code PENDING_CLICK} row when device/ip fingerprints align,
 *       or inserted fresh otherwise.</li>
 * </ul>
 *
 * <p>{@code refereeUserId} is null on PENDING_CLICK rows and populated
 * on SIGNED_UP / ACTIVATED rows. The unique index
 * {@code uq_referrals_referee} guarantees one signup is credited to at
 * most one inviter — even if attribution is retried.
 *
 * <p>Fraud columns are intentionally cheap: hashed IP + device id (so
 * we never store PII), a coarse user-agent bucket, and a free-form
 * {@code fraudFlags} text column where the fraud service appends short
 * tags ("self-referral", "ip-velocity", "disposable-email").
 */
@Entity
@Table(name = "referrals",
        indexes = {
                @Index(name = "idx_referrals_referrer", columnList = "referrer_user_id, status, created_at"),
                @Index(name = "idx_referrals_code", columnList = "code"),
                @Index(name = "idx_referrals_ip_hash", columnList = "ip_hash, created_at"),
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_referrals_referee", columnNames = "referee_user_id")
        })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The inviter — every referral must point to a real user. */
    @Column(name = "referrer_user_id", nullable = false)
    private Long referrerUserId;

    /** The signed-up user. Null until status reaches {@code SIGNED_UP}. */
    @Column(name = "referee_user_id")
    private Long refereeUserId;

    /**
     * Snapshot of the inviter's code at the time the referral was
     * created — kept even after the inviter rotates their code so the
     * audit trail stays intact.
     */
    @Column(nullable = false, length = 16)
    private String code;

    /** "LINK", "QR", "MESSAGE", "CONTACTS", "OTHER". */
    @Column(length = 24)
    @Builder.Default
    private String source = "LINK";

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    @Builder.Default
    private ReferralStatus status = ReferralStatus.PENDING_CLICK;

    /** Was the inviter a creator at the moment of attribution? */
    @Column(name = "creator_referral", nullable = false)
    @Builder.Default
    private boolean creatorReferral = false;

    // ----- fraud / attribution fingerprints (hashed, never raw) ----------

    /** SHA-256 of the client IP. Used for velocity + dedup. */
    @Column(name = "ip_hash", length = 64)
    private String ipHash;

    /** SHA-256 of a client-supplied device id. Optional. */
    @Column(name = "device_hash", length = 64)
    private String deviceHash;

    /** Coarse user-agent bucket ("ios", "android", "web", "other"). */
    @Column(name = "ua_bucket", length = 16)
    private String uaBucket;

    /** Coma-separated tags appended by the fraud service. Empty == clean. */
    @Column(name = "fraud_flags", length = 255)
    @Builder.Default
    private String fraudFlags = "";

    // ----- timestamps ----------------------------------------------------

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "signed_up_at")
    private LocalDateTime signedUpAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = ReferralStatus.PENDING_CLICK;
    }
}
