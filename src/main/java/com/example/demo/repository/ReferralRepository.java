package com.example.demo.repository;

import com.example.demo.entity.Referral;
import com.example.demo.entity.ReferralStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ReferralRepository extends JpaRepository<Referral, Long> {

    Optional<Referral> findByRefereeUserId(Long refereeUserId);

    long countByReferrerUserIdAndStatus(Long referrerUserId, ReferralStatus status);

    long countByReferrerUserId(Long referrerUserId);

    /** Paginated list of someone's referrals, newest first. */
    @Query("""
           SELECT r FROM Referral r
            WHERE r.referrerUserId = :referrerUserId
            ORDER BY r.createdAt DESC
           """)
    List<Referral> findByReferrer(@Param("referrerUserId") Long referrerUserId,
                                   Pageable page);

    /**
     * IP velocity probe — how many signups have we credited from this
     * hashed IP in the last window. The fraud service uses this to
     * flag (not block) suspicious clusters.
     */
    @Query("""
           SELECT COUNT(r) FROM Referral r
            WHERE r.ipHash = :ipHash
              AND r.status IN (com.example.demo.entity.ReferralStatus.SIGNED_UP,
                                com.example.demo.entity.ReferralStatus.ACTIVATED)
              AND r.createdAt > :since
           """)
    long countSignupsFromIpSince(@Param("ipHash") String ipHash,
                                  @Param("since") LocalDateTime since);

    /**
     * Time-bucketed growth — used by the dashboard's mini-chart. Returns
     * one row per UTC day in the requested window:
     * {@code [java.sql.Date, Long signups]}.
     */
    @Query("""
           SELECT CAST(r.signedUpAt AS date), COUNT(r)
             FROM Referral r
            WHERE r.referrerUserId = :referrerUserId
              AND r.status IN (com.example.demo.entity.ReferralStatus.SIGNED_UP,
                                com.example.demo.entity.ReferralStatus.ACTIVATED)
              AND r.signedUpAt >= :since
            GROUP BY CAST(r.signedUpAt AS date)
            ORDER BY CAST(r.signedUpAt AS date) ASC
           """)
    List<Object[]> dailySignupCounts(@Param("referrerUserId") Long referrerUserId,
                                      @Param("since") LocalDateTime since);

    /**
     * Promote a pending click to a signup. Used when attribution
     * happens after the user has already loaded the landing page on
     * the same hashed device — keeps the funnel row count honest.
     */
    @Query("""
           SELECT r FROM Referral r
            WHERE r.code = :code
              AND r.status = com.example.demo.entity.ReferralStatus.PENDING_CLICK
              AND r.deviceHash = :deviceHash
              AND r.refereeUserId IS NULL
            ORDER BY r.createdAt DESC
           """)
    List<Referral> findPromotablePendingClick(@Param("code") String code,
                                               @Param("deviceHash") String deviceHash,
                                               Pageable limit);
}
