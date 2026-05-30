package com.example.demo.recommendation;

import org.springframework.stereotype.Component;

/**
 * Default heuristic ranker. Tuned to feel like a starter-pack TikTok
 * ranker: strongly weight personalisation (creator + topic affinity),
 * keep watch-based engagement second, and inject freshness + a little
 * trending so the feed doesn't get stale.
 *
 * <p>This implementation runs entirely in-process and is the fallback
 * the recommendation pipeline always has available even if no ML model
 * has been registered.</p>
 */
@Component
public class HeuristicRankingModel implements RankingModel {

    // Coefficients — kept package-visible so they can be tuned from tests.
    static final double W_CREATOR    = 2.0;
    static final double W_TOPIC      = 1.5;
    static final double W_FRESHNESS  = 1.0;
    static final double W_QUALITY    = 1.2;
    static final double W_ENGAGE     = 0.8;
    static final double W_SOCIAL     = 0.6;
    static final double W_TRENDING   = 0.7;
    static final double W_DIVERSITY  = 1.0;   // multiplied by an already-negative penalty

    @Override
    public String id() {
        return "heuristic.v1";
    }

    @Override
    public int priority() {
        return 0;
    }

    @Override
    public double score(FeatureVector f) {
        return  W_CREATOR   * f.get(FeatureVector.CREATOR_AFFINITY)
              + W_TOPIC     * f.get(FeatureVector.TOPIC_AFFINITY)
              + W_FRESHNESS * f.get(FeatureVector.FRESHNESS)
              + W_QUALITY   * f.get(FeatureVector.CREATOR_QUALITY)
              + W_ENGAGE    * f.get(FeatureVector.ENGAGEMENT)
              + W_SOCIAL    * f.get(FeatureVector.SOCIAL_PROOF)
              + W_TRENDING  * f.get(FeatureVector.TRENDING)
              + W_DIVERSITY * f.get(FeatureVector.DIVERSITY_PEN);
    }
}
