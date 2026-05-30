package com.example.demo.recommendation;

/**
 * One pluggable ranking algorithm.
 *
 * <p>The recommendation pipeline builds a {@link FeatureVector} per
 * candidate item then asks every registered {@link RankingModel} for a
 * score. The {@link ModelRegistry} picks the active one (e.g. heuristic
 * by default, ML-backed once plugged in).</p>
 */
public interface RankingModel {

    /** Stable identifier used for routing / A-B testing / logging. */
    String id();

    /**
     * Returns the score for the candidate represented by {@code features}.
     * Higher = more relevant. Scale is unspecified; ranking is by sort
     * order only, never by absolute value.
     */
    double score(FeatureVector features);

    /** Higher-priority models win in the {@link ModelRegistry}. Lets the
     *  app register a heuristic as fallback and an ML model on top. */
    default int priority() { return 0; }
}
