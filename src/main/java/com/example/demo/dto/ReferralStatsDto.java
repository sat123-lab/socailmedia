package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Aggregated stats for the referral dashboard. Holds three things:
 * <ul>
 *   <li>top-line counters (invited / signed up / activated),</li>
 *   <li>a small daily growth series for the chart,</li>
 *   <li>and a "creator referrals" subtotal — useful to creators who
 *       want to see how many of their incoming users came in via their
 *       referral system specifically.</li>
 * </ul>
 *
 * <p>Conversion rate is computed client-side from the counters so we
 * don't have to round at the API boundary.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferralStatsDto {

    private long invited;
    private long signedUp;
    private long activated;
    private long revoked;

    /** Subset of the above — counts where {@code creatorReferral=true}. */
    private long creatorSignups;

    /**
     * Daily counts for a small chart. Keys are ISO-8601 dates
     * (yyyy-MM-dd); values are the number of signed-up referrals
     * credited on that day. Sparse — days with zero signups are
     * omitted, the client fills gaps as needed.
     */
    private Map<String, Long> growthDaily;

    /** A short list of {@code "kind"} → count for the "Funnel" widget. */
    private List<Map<String, Object>> funnel;
}
