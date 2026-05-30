package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.repository.ReferralRepository;
import com.example.demo.security.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Cheap, hookable fraud heuristics for the referral pipeline.
 *
 * <p>None of these are hard blocks except {@link #ensureNotSelfReferral}.
 * The rest return tags ({@code "ip-velocity"}, {@code "disposable-email"})
 * that the service records in {@code fraudFlags} so the reward engine
 * (when it ships) can gate payouts on a clean record without us having
 * to retro-fit anything.
 *
 * <p>Privacy: every IP and device id is hashed at the edge via
 * {@link HashUtil#sha256Hex(String)} so we never store raw fingerprints.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReferralFraudService {

    private final ReferralRepository referrals;

    /** Per-IP per-window threshold above which we flag (not block). */
    private static final int IP_VELOCITY_THRESHOLD = 5;
    private static final int IP_VELOCITY_WINDOW_HOURS = 1;

    /**
     * Hard rule — a user cannot refer themselves. Throws so the
     * controller returns 400. Caller passes the inviter + the brand
     * new signup; we accept either {@code refereeEmail} or
     * {@code refereePhone} for the comparison.
     */
    public void ensureNotSelfReferral(User inviter, String refereeEmail,
                                       String refereePhone) {
        if (inviter == null) return;
        if (refereeEmail != null
                && inviter.getEmail() != null
                && refereeEmail.equalsIgnoreCase(inviter.getEmail())) {
            throw new IllegalArgumentException(
                    "Self-referral rejected: email matches inviter");
        }
        if (refereePhone != null
                && inviter.getPhone() != null
                && refereePhone.equals(inviter.getPhone())) {
            throw new IllegalArgumentException(
                    "Self-referral rejected: phone matches inviter");
        }
    }

    /**
     * Build an opaque hash of the client IP. Returns empty string when
     * the caller has no IP (e.g. tests).
     */
    public String hashIp(String ipAddress) {
        return HashUtil.sha256Hex(ipAddress == null ? "" : ipAddress);
    }

    public String hashDevice(String deviceId) {
        return HashUtil.sha256Hex(deviceId == null ? "" : deviceId);
    }

    /**
     * Bucket a user agent into {@code ios / android / web / other}.
     * Granular UA strings are noise for our fraud table — buckets are
     * enough to spot a campaign coming from a single source.
     */
    public String bucketUserAgent(String ua) {
        if (ua == null) return "other";
        String l = ua.toLowerCase(Locale.ROOT);
        if (l.contains("iphone") || l.contains("ipad") || l.contains("darwin")) {
            return "ios";
        }
        if (l.contains("android") || l.contains("dalvik")) return "android";
        if (l.contains("mozilla") || l.contains("chrome") || l.contains("safari")) {
            return "web";
        }
        return "other";
    }

    /**
     * Compute the set of fraud flags for a referral candidate. Pure
     * function — the service decides what to do with them (record /
     * reject / silently activate). All flags are short slugs so the
     * UI / admin tool can render them as chips.
     */
    public List<String> detectFlags(SignupContext ctx) {
        List<String> flags = new ArrayList<>();
        if (looksDisposable(ctx.email)) flags.add("disposable-email");
        if (isPlusAlias(ctx.email)) flags.add("email-plus-alias");
        if (ctx.ipHash != null && !ctx.ipHash.isEmpty()) {
            long since = referrals.countSignupsFromIpSince(
                    ctx.ipHash,
                    LocalDateTime.now().minusHours(IP_VELOCITY_WINDOW_HOURS));
            if (since >= IP_VELOCITY_THRESHOLD) {
                flags.add("ip-velocity:" + since);
            }
        }
        if (ctx.deviceHash != null && !ctx.deviceHash.isEmpty()) {
            // Re-using a device hash with a different signup is suspicious
            // — same phone joining twice via different codes. Cheap query.
            // (We use the same table the velocity check just hit, so the
            // cache is warm.)
            long since = referrals.countSignupsFromIpSince(
                    ctx.deviceHash,
                    LocalDateTime.now().minusHours(IP_VELOCITY_WINDOW_HOURS * 6));
            if (since >= 2) flags.add("device-reuse:" + since);
        }
        return flags;
    }

    // -------- private heuristics ---------------------------------------

    /**
     * Lightweight disposable-email guard. We only block a handful of
     * popular throwaway providers — false negatives are preferable to
     * false positives at this layer.
     */
    private static final Set<String> DISPOSABLE_DOMAINS = Set.of(
            "mailinator.com", "yopmail.com", "guerrillamail.com",
            "10minutemail.com", "tempmail.com", "throwawaymail.com",
            "discard.email", "trashmail.com", "sharklasers.com"
    );

    private boolean looksDisposable(String email) {
        if (email == null) return false;
        int at = email.lastIndexOf('@');
        if (at < 0) return false;
        return DISPOSABLE_DOMAINS.contains(
                email.substring(at + 1).toLowerCase(Locale.ROOT));
    }

    private boolean isPlusAlias(String email) {
        if (email == null) return false;
        int at = email.indexOf('@');
        if (at < 0) return false;
        return email.substring(0, at).contains("+");
    }

    /**
     * Input bundle for {@link #detectFlags(SignupContext)} — keeps the
     * signature small as we add new heuristics over time.
     */
    public static class SignupContext {
        public String email;
        public String ipHash;
        public String deviceHash;

        public SignupContext(String email, String ipHash, String deviceHash) {
            this.email = email;
            this.ipHash = ipHash;
            this.deviceHash = deviceHash;
        }
    }
}
