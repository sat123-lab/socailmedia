package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Returned by {@code GET /referrals/me} — everything the Invite Friends
 * screen needs to render the share UI. The {@code link} is built
 * server-side using {@code app.public-web-url} so swapping the marketing
 * domain doesn't require a client release.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralCodeDto {

    /** Inviter's user id — used by the client-side dashboard hero. */
    private Long userId;

    /** 8-character user-facing code (e.g. {@code 9F4HKMDX}). */
    private String code;

    /** Full https URL safe to copy / share. */
    private String link;

    /**
     * Human-friendly share message — emoji + tagline + link. The
     * client uses this verbatim when invoking {@code share_plus}.
     */
    private String shareMessage;

    /** Convenience snapshot — saves an extra round-trip on the screen. */
    private long invited;
    private long signedUp;
    private long activated;

    /** True if the inviter is a CREATOR / BUSINESS account. */
    private boolean creator;
}
