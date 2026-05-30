package com.example.demo.recommendation;

import java.util.List;
import java.util.Map;

/**
 * Pluggable abstractions for future AI-served features. Each provider
 * is intentionally narrow — one job per interface — so the recommender
 * can mix-and-match cloud and self-hosted models without rewiring.
 *
 * <p>No implementations ship in this build; the recommender just calls
 * {@code Optional.ofNullable(provider).ifPresent(...)} and falls back
 * to the heuristic path when none is registered.</p>
 */
public final class AIProviders {
    private AIProviders() {}

    /** Item-level relevance score for one (user, item) pair. */
    public interface RankingAIProvider {
        double score(long userId, String targetType, long targetId,
                     Map<String, Double> features);
    }

    /** "People you may know" / similar creators / etc. */
    public interface CreatorScoringAIProvider {
        List<Long> recommendCreators(long userId, int max);
    }

    /** Predicts what's about to trend — feeds the trending injection
     *  step ahead of pure-signal recompute. */
    public interface TrendPredictionAIProvider {
        List<String> upcomingHashtags(int max);
    }

    /** Flags spammy or low-quality content so the ranker can suppress it. */
    public interface SpamDetectionAIProvider {
        boolean isSpam(String targetType, long targetId);
    }

    /** Soft-moderation hook — gives a probability the item violates rules. */
    public interface ModerationAIProvider {
        double riskScore(String targetType, long targetId);
    }
}
