package com.example.demo.entity;

/**
 * Lifecycle a single referral row can go through.
 *
 * <ul>
 *   <li>{@link #PENDING_CLICK} — the link was opened on the landing page
 *       but no signup happened yet. Stored anonymously (no referee_id).</li>
 *   <li>{@link #SIGNED_UP} — a real account was created and credited to
 *       the inviter. {@code refereeUserId} is filled in.</li>
 *   <li>{@link #ACTIVATED} — the referee performed a meaningful action
 *       (first post / follow / message). Used by the future rewards
 *       engine as the actual eligibility gate.</li>
 *   <li>{@link #REVOKED} — moderator action, fraud detection, or
 *       account deletion. Counts against neither party.</li>
 * </ul>
 *
 * No payment / reward state intentionally — the user asked us not to
 * add payments yet. {@link #ACTIVATED} is the natural attachment point
 * when that ships.
 */
public enum ReferralStatus {
    PENDING_CLICK,
    SIGNED_UP,
    ACTIVATED,
    REVOKED
}
