package com.example.demo.recommendation;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Singleton holding the registered {@link AIProviders} implementations.
 *
 * <p>An external plugin (Spring config in another module, a feature flag
 * service, an ML server bootstrap class, etc.) registers its providers
 * at startup; the recommender consults this service before falling
 * back to the heuristic path.</p>
 */
@Component
public class RecommendationAIService {

    private final AtomicReference<AIProviders.RankingAIProvider>         ranking      = new AtomicReference<>();
    private final AtomicReference<AIProviders.CreatorScoringAIProvider>  creators     = new AtomicReference<>();
    private final AtomicReference<AIProviders.TrendPredictionAIProvider> trends       = new AtomicReference<>();
    private final AtomicReference<AIProviders.SpamDetectionAIProvider>   spam         = new AtomicReference<>();
    private final AtomicReference<AIProviders.ModerationAIProvider>      moderation   = new AtomicReference<>();

    public void register(AIProviders.RankingAIProvider p)        { ranking.set(p); }
    public void register(AIProviders.CreatorScoringAIProvider p) { creators.set(p); }
    public void register(AIProviders.TrendPredictionAIProvider p){ trends.set(p); }
    public void register(AIProviders.SpamDetectionAIProvider p)  { spam.set(p); }
    public void register(AIProviders.ModerationAIProvider p)     { moderation.set(p); }

    public Optional<AIProviders.RankingAIProvider>         ranking()    { return Optional.ofNullable(ranking.get()); }
    public Optional<AIProviders.CreatorScoringAIProvider>  creators()   { return Optional.ofNullable(creators.get()); }
    public Optional<AIProviders.TrendPredictionAIProvider> trends()     { return Optional.ofNullable(trends.get()); }
    public Optional<AIProviders.SpamDetectionAIProvider>   spam()       { return Optional.ofNullable(spam.get()); }
    public Optional<AIProviders.ModerationAIProvider>      moderation() { return Optional.ofNullable(moderation.get()); }
}
