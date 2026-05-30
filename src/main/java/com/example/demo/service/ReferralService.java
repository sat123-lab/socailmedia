package com.example.demo.service;

import com.example.demo.dto.ReferralCodeDto;
import com.example.demo.dto.ReferralListItemDto;
import com.example.demo.dto.ReferralStatsDto;
import com.example.demo.entity.Referral;
import com.example.demo.entity.ReferralStatus;
import com.example.demo.entity.User;
import com.example.demo.repository.ReferralRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ReferralFraudService.SignupContext;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level orchestrator for the Referral & Invite system.
 *
 * <p>Three flows live here:
 * <ol>
 *   <li>{@link #me(User)} — the inviter's "share UI" bundle.</li>
 *   <li>{@link #recordClick} — anonymous landing-page click tracking.</li>
 *   <li>{@link #attributeSignup} — credits a brand-new account to the
 *       inviter, applying fraud heuristics + self-referral guard.</li>
 * </ol>
 *
 * <p>The service intentionally keeps no notion of "rewards" yet —
 * activation is recorded ({@link #markActivated(Long)}) but no payout
 * is computed. The future rewards engine plugs onto the {@code ACTIVATED}
 * transition without changing this class.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReferralService {

    private final ReferralRepository referrals;
    private final UserRepository users;
    private final ReferralCodeService codes;
    private final ReferralFraudService fraud;

    /**
     * Public marketing URL used to build the invite link. Defaults to
     * the API base when nothing is configured — that gives a working
     * (if ugly) link out of the box.
     */
    @Value("${app.public-web-url:http://localhost:5173}")
    private String publicWebUrl;

    // ========================================================================
    //  /referrals/me
    // ========================================================================

    @Transactional
    public ReferralCodeDto me(User user) {
        String code = codes.ensureCode(user);
        long invited = referrals.countByReferrerUserId(user.getId());
        long signedUp = referrals.countByReferrerUserIdAndStatus(
                user.getId(), ReferralStatus.SIGNED_UP)
                + referrals.countByReferrerUserIdAndStatus(
                        user.getId(), ReferralStatus.ACTIVATED);
        long activated = referrals.countByReferrerUserIdAndStatus(
                user.getId(), ReferralStatus.ACTIVATED);
        return ReferralCodeDto.builder()
                .userId(user.getId())
                .code(code)
                .link(buildLink(code))
                .shareMessage(buildShareMessage(user, code))
                .invited(invited)
                .signedUp(signedUp)
                .activated(activated)
                .creator(isCreator(user))
                .build();
    }

    // ========================================================================
    //  Click tracking — anonymous, IP-rate-limited at the controller
    // ========================================================================

    @Transactional
    public void recordClick(String rawCode, String ipAddress, String deviceId,
                             String userAgent, String source) {
        String code = ReferralCodeService.normalise(rawCode);
        if (code == null || code.isBlank()) return;
        User inviter = codes.resolve(code);
        if (inviter == null) return;
        Referral row = Referral.builder()
                .referrerUserId(inviter.getId())
                .code(code)
                .source(source == null ? "LINK" : source)
                .status(ReferralStatus.PENDING_CLICK)
                .ipHash(fraud.hashIp(ipAddress))
                .deviceHash(fraud.hashDevice(deviceId))
                .uaBucket(fraud.bucketUserAgent(userAgent))
                .creatorReferral(isCreator(inviter))
                .createdAt(LocalDateTime.now())
                .build();
        referrals.save(row);
    }

    // ========================================================================
    //  Signup attribution
    // ========================================================================

    /**
     * Called by the auth flow after a brand-new account is persisted.
     *
     * <p>Idempotent — calling twice for the same {@code refereeUser} is
     * a no-op (we use the unique index on {@code referee_user_id}).
     *
     * <p>Returns the saved row (or {@code null} when no attribution was
     * possible / the code didn't resolve / self-referral was rejected).
     */
    @Transactional
    public Referral attributeSignup(String rawCode,
                                     User refereeUser,
                                     String ipAddress,
                                     String deviceId,
                                     String userAgent,
                                     String source) {
        if (refereeUser == null || refereeUser.getId() == null) return null;
        if (referrals.findByRefereeUserId(refereeUser.getId()).isPresent()) {
            // Already attributed — no double-credit.
            return null;
        }
        String code = ReferralCodeService.normalise(rawCode);
        if (code == null || code.isBlank()) return null;
        User inviter = codes.resolve(code);
        if (inviter == null) {
            log.info("Referral attribution failed — unknown code {}", code);
            return null;
        }
        // Hard rule.
        try {
            fraud.ensureNotSelfReferral(inviter,
                    refereeUser.getEmail(), refereeUser.getPhone());
        } catch (IllegalArgumentException e) {
            log.warn("Referral self-referral rejected for user {}: {}",
                    refereeUser.getId(), e.getMessage());
            return null;
        }

        String ipHash = fraud.hashIp(ipAddress);
        String deviceHash = fraud.hashDevice(deviceId);
        List<String> flags = fraud.detectFlags(
                new SignupContext(refereeUser.getEmail(), ipHash, deviceHash));

        // Try to promote a matching pending click (same code + device)
        // so the funnel stays accurate.
        Referral row = referrals.findPromotablePendingClick(
                code, deviceHash, PageRequest.of(0, 1)).stream()
                .findFirst().orElse(null);
        if (row == null) {
            row = Referral.builder()
                    .referrerUserId(inviter.getId())
                    .code(code)
                    .source(source == null ? "LINK" : source)
                    .ipHash(ipHash)
                    .deviceHash(deviceHash)
                    .uaBucket(fraud.bucketUserAgent(userAgent))
                    .creatorReferral(isCreator(inviter))
                    .build();
        }
        row.setRefereeUserId(refereeUser.getId());
        row.setStatus(ReferralStatus.SIGNED_UP);
        row.setSignedUpAt(LocalDateTime.now());
        if (!flags.isEmpty()) {
            row.setFraudFlags(String.join(",", flags));
        }
        referrals.save(row);

        // Persist immutable back-pointer on the user — saves a join in
        // hot paths, and gives the rewards engine a place to land.
        refereeUser.setReferredByUserId(inviter.getId());
        users.save(refereeUser);
        log.info("Referral attributed: inviter={} referee={} flags={}",
                inviter.getId(), refereeUser.getId(), flags);
        return row;
    }

    /**
     * Promote a SIGNED_UP referral to ACTIVATED. Called when the
     * referee performs a meaningful action (first post / follow /
     * message). Idempotent; safe to call many times — only flips the
     * status once.
     *
     * <p>This is the gate the future rewards engine will key off.
     */
    @Transactional
    public void markActivated(Long refereeUserId) {
        if (refereeUserId == null) return;
        referrals.findByRefereeUserId(refereeUserId).ifPresent(r -> {
            if (r.getStatus() == ReferralStatus.SIGNED_UP) {
                r.setStatus(ReferralStatus.ACTIVATED);
                r.setActivatedAt(LocalDateTime.now());
                referrals.save(r);
                log.info("Referral activated: id={} referee={}",
                        r.getId(), refereeUserId);
            }
        });
    }

    // ========================================================================
    //  Dashboard
    // ========================================================================

    public List<ReferralListItemDto> list(Long inviterId, int limit) {
        var rows = referrals.findByReferrer(
                inviterId, PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
        if (rows.isEmpty()) return List.of();
        // Hydrate referee chrome.
        var refereeIds = rows.stream()
                .map(Referral::getRefereeUserId)
                .filter(java.util.Objects::nonNull)
                .toList();
        Map<Long, User> userMap = new HashMap<>();
        for (User u : users.findAllById(refereeIds)) userMap.put(u.getId(), u);
        List<ReferralListItemDto> out = new ArrayList<>(rows.size());
        for (Referral r : rows) {
            out.add(ReferralListItemDto.from(r,
                    r.getRefereeUserId() == null
                            ? null : userMap.get(r.getRefereeUserId())));
        }
        return out;
    }

    public ReferralStatsDto stats(Long inviterId) {
        long invited = referrals.countByReferrerUserId(inviterId);
        long signedUp = referrals.countByReferrerUserIdAndStatus(
                inviterId, ReferralStatus.SIGNED_UP)
                + referrals.countByReferrerUserIdAndStatus(
                        inviterId, ReferralStatus.ACTIVATED);
        long activated = referrals.countByReferrerUserIdAndStatus(
                inviterId, ReferralStatus.ACTIVATED);
        long revoked = referrals.countByReferrerUserIdAndStatus(
                inviterId, ReferralStatus.REVOKED);

        // Daily growth — past 30 days.
        var raw = referrals.dailySignupCounts(
                inviterId, LocalDateTime.now().minusDays(30));
        Map<String, Long> daily = new LinkedHashMap<>();
        for (Object[] row : raw) {
            // row[0] is java.sql.Date or java.time.LocalDate depending on
            // the driver; .toString() yields ISO-8601 in either case.
            daily.put(String.valueOf(row[0]),
                    row[1] == null ? 0 : ((Number) row[1]).longValue());
        }

        // Creator-specific subtotal — saves an extra query by counting
        // the (very small) recent set client-side.
        long creatorSignups = referrals.findByReferrer(
                        inviterId, PageRequest.of(0, 200))
                .stream()
                .filter(r -> r.isCreatorReferral())
                .filter(r -> r.getStatus() == ReferralStatus.SIGNED_UP
                        || r.getStatus() == ReferralStatus.ACTIVATED)
                .count();

        var funnel = new ArrayList<Map<String, Object>>();
        funnel.add(Map.of("kind", "INVITED",   "count", invited));
        funnel.add(Map.of("kind", "SIGNED_UP", "count", signedUp));
        funnel.add(Map.of("kind", "ACTIVATED", "count", activated));

        return ReferralStatsDto.builder()
                .invited(invited)
                .signedUp(signedUp)
                .activated(activated)
                .revoked(revoked)
                .creatorSignups(creatorSignups)
                .growthDaily(daily)
                .funnel(funnel)
                .build();
    }

    /**
     * Lightweight resolver for the public landing page. Returns the
     * inviter's display chrome only — no email/phone.
     */
    public Map<String, Object> resolvePublic(String code) {
        User u = codes.resolve(code);
        if (u == null) {
            throw new EntityNotFoundException("Unknown referral code");
        }
        return Map.of(
                "code", ReferralCodeService.normalise(code),
                "inviterId", u.getId(),
                "inviterName", u.getName() == null ? "" : u.getName(),
                "inviterAvatar",
                        u.getProfileImage() == null ? "" : u.getProfileImage(),
                "inviterVerified", u.isVerified(),
                "inviterIsCreator", isCreator(u),
                "appLink", buildLink(ReferralCodeService.normalise(code))
        );
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    private boolean isCreator(User u) {
        if (u == null || u.getAccountType() == null) return false;
        return !"PERSONAL".equalsIgnoreCase(u.getAccountType());
    }

    private String buildLink(String code) {
        String base = publicWebUrl;
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/r/" + code;
    }

    private String buildShareMessage(User inviter, String code) {
        String name = inviter == null || inviter.getName() == null
                ? "I" : inviter.getName();
        return "%s sent you an invite. Join us — %s"
                .formatted(name, buildLink(code));
    }
}
