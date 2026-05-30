package com.example.demo.recommendation;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The bag-of-features a ranking model sees for one (user, item) pair.
 *
 * <p>Implemented as a typed dictionary instead of a record so future
 * features can be added without rippling through every ranker
 * implementation, and so ML models can ingest the dict directly.</p>
 *
 * <p>Standard keys are listed as constants below. Heuristic rankers use
 * them; future ML models can add their own without breaking anything.</p>
 */
public final class FeatureVector {

    public static final String CREATOR_AFFINITY = "creatorAffinity";
    public static final String TOPIC_AFFINITY   = "topicAffinity";
    public static final String FRESHNESS        = "freshness";       // [0..1]
    public static final String CREATOR_QUALITY  = "creatorQuality";  // [0..]
    public static final String ENGAGEMENT       = "engagement";      // global on item
    public static final String SOCIAL_PROOF     = "socialProof";     // friends-liked etc.
    public static final String DIVERSITY_PEN    = "diversityPenalty"; // negative
    public static final String TRENDING         = "trending";

    private final Map<String, Double> values = new LinkedHashMap<>();

    public FeatureVector put(String k, double v) {
        values.put(k, v);
        return this;
    }

    public double get(String k) {
        return values.getOrDefault(k, 0.0);
    }

    public boolean has(String k) {
        return values.containsKey(k);
    }

    public Map<String, Double> asMap() {
        return Map.copyOf(values);
    }
}
