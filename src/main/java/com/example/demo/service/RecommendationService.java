package com.example.demo.service;

import com.example.demo.entity.*;
import com.example.demo.recommendation.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Top-level orchestration of the recommendation pipeline.
 *
 * <p>This service is intentionally model-agnostic: it builds
 * {@link FeatureVector}s for candidate items and delegates the actual
 * scoring to {@link ModelRegistry#active()}. Swap the model and the
 * feed reranks accordingly.</p>
 *
 * <p>Exposed entry points:</p>
 * <ul>
 *   <li>{@link #rankReels} — rerank a candidate set of reels for one user.</li>
 *   <li>{@link #suggestedCreators} — collaborative-filter creator discovery.</li>
 *   <li>{@link #personalizedHomeFeed} — mix-and-rank posts + reels + lives + stories.</li>
 *   <li>{@link #interestProfile} — debug snapshot of the interest vector.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private static final double FRESHNESS_HALF_LIFE_HOURS = 72;  // 3 days
    private static final int    MAX_TOPICS_LOADED         = 32;
    private static final int    DIVERSITY_LIMIT_PER_CREATOR = 2;

    private final UserInterestRepository interests;
    private final CreatorAffinityRepository affinities;
    private final TrendingItemRepository trending;
    private final CreatorStatsRtRepository creatorStats;
    private final ReelRepository reels;
    private final ModelRegistry modelRegistry;
    private final RecommendationAIService aiService;

    // =====================================================================
    //  REEL RANKING
    // =====================================================================

    /**
     * Reranks a candidate set of reels for one viewer using the active
     * ranking model. Applies a creator-diversity pass at the end so the
     * feed doesn't fill up with the same person's content even if their
     * affinity score is dominant.
     */
    public List<Reel> rankReels(Long viewerId, List<Reel> candidates) {
        if (candidates == null || candidates.isEmpty()) return List.of();
        if (viewerId == null) {
            // Anonymous viewer — fall back to global trending + freshness.
            return candidates.stream()
                    .sorted(Comparator.comparingDouble(this::globalScore).reversed())
                    .toList();
        }

        ProfileContext ctx = loadProfile(viewerId);
        RankingModel model = modelRegistry.active();

        // Score every candidate, then apply diversity cap.
        record Scored(Reel reel, double score) {}
        List<Scored> scored = new ArrayList<>(candidates.size());
        for (Reel r : candidates) {
            FeatureVector f = featuresForReel(viewerId, r, ctx);
            double s = aiService.ranking()
                    .map(p -> p.score(viewerId, SignalKinds.T_REEL, r.getId(), f.asMap()))
                    .orElseGet(() -> model.score(f));
            scored.add(new Scored(r, s));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());

        // Diversity pass — re-shuffle so no creator dominates the top of the feed.
        Map<Long, Integer> perCreator = new HashMap<>();
        List<Scored> out = new ArrayList<>(scored.size());
        List<Scored> overflow = new ArrayList<>();
        for (Scored s : scored) {
            int c = perCreator.getOrDefault(s.reel().getUserId(), 0);
            if (c >= DIVERSITY_LIMIT_PER_CREATOR) {
                overflow.add(s);
            } else {
                perCreator.merge(s.reel().getUserId(), 1, Integer::sum);
                out.add(s);
            }
        }
        out.addAll(overflow);
        return out.stream().map(Scored::reel).toList();
    }

    /**
     * Builds the feature vector for one (user, reel) pair. Pure function
     * over the loaded profile context + the reel — no hidden DB reads.
     */
    private FeatureVector featuresForReel(Long viewerId, Reel r, ProfileContext ctx) {
        FeatureVector f = new FeatureVector();

        // Creator affinity — non-negative.
        f.put(FeatureVector.CREATOR_AFFINITY,
                ctx.creatorScores.getOrDefault(r.getUserId(), 0.0));

        // Topic affinity — sum of (topic score) over the reel's hashtags.
        double topic = 0;
        if (r.getHashtags() != null) {
            for (String raw : r.getHashtags().split("[ ,#]+")) {
                if (raw == null || raw.isBlank()) continue;
                String norm = InterestGraphService.normaliseTopic(raw);
                topic += ctx.topicScores.getOrDefault(norm, 0.0);
            }
        }
        f.put(FeatureVector.TOPIC_AFFINITY, topic);

        // Freshness — exponential decay over hours.
        double ageHours = ageInHours(r.getCreatedAt());
        f.put(FeatureVector.FRESHNESS,
                Math.exp(-ageHours / FRESHNESS_HALF_LIFE_HOURS));

        // Creator quality — looked up from the precomputed snapshot.
        f.put(FeatureVector.CREATOR_QUALITY,
                ctx.creatorQuality.getOrDefault(r.getUserId(), 0.0));

        // Global engagement — normalised by log(views) to dampen big numbers.
        double eng = (r.getLikesCount() + 3.0 * r.getCommentsCount()
                    + 5.0 * r.getSharesCount() + r.getWatchTimeSeconds() / 10.0)
                    / Math.log(Math.max(2, r.getViewsCount()) + Math.E);
        f.put(FeatureVector.ENGAGEMENT, eng);

        // Social proof — boost reels by creators followed by friends-of-friends
        // (cheap proxy: any creator I have a positive affinity with).
        f.put(FeatureVector.SOCIAL_PROOF,
                ctx.followedIds.contains(r.getUserId()) ? 1.0 : 0.0);

        // Trending — straight from the precomputed table.
        f.put(FeatureVector.TRENDING,
                ctx.trendingReelIds.getOrDefault(r.getId(), 0.0));

        // Diversity penalty — applied as a small negative if the creator
        // already showed up at the very top of this user's feed (the
        // diversity cap is the hard rule; this is a softener for ties).
        long alreadySeen = ctx.creatorHistogram
                .getOrDefault(r.getUserId(), 0L);
        f.put(FeatureVector.DIVERSITY_PEN, -0.25 * Math.min(alreadySeen, 4));

        return f;
    }

    /** Anonymous-viewer ranking score: trending + freshness only. */
    private double globalScore(Reel r) {
        double freshness = Math.exp(-ageInHours(r.getCreatedAt())
                / FRESHNESS_HALF_LIFE_HOURS);
        return r.getTrendingScore() + 0.5 * freshness;
    }

    private static double ageInHours(LocalDateTime ts) {
        if (ts == null) return 0;
        long sec = Duration.between(ts, LocalDateTime.now()).toSeconds();
        return Math.max(0, sec / 3600.0);
    }

    // =====================================================================
    //  CREATOR DISCOVERY
    // =====================================================================

    /**
     * "People you may know" — combines co-occurrence on the affinity
     * graph with an optional AI provider override.
     */
    public List<Long> suggestedCreators(Long userId, int max) {
        // 1. AI provider gets first dibs.
        Optional<List<Long>> ai = aiService.creators()
                .map(p -> p.recommendCreators(userId, max));
        if (ai.isPresent()) return ai.get();

        // 2. Heuristic — collaborative filter on the affinity table.
        List<Object[]> rows = affinities.recommendByCoOccurrence(userId, max);
        List<Long> ids = rows.stream()
                .map(r -> ((Number) r[0]).longValue())
                .toList();

        if (ids.isEmpty()) {
            // 3. Cold start — top creators by global quality score.
            ids = creatorStats.findByOrderByQualityScoreDesc(
                            PageRequest.of(0, max)).stream()
                    .map(CreatorStatsRt::getCreatorId)
                    .toList();
        }
        return ids;
    }

    // =====================================================================
    //  HOME FEED (mixed)
    // =====================================================================

    /**
     * Personalised home feed — currently sources reels only (the existing
     * post feed lives in {@code PostController#feed} and is wired
     * separately). This entry point becomes the single mixer once the
     * other content types are integrated.
     */
    public List<Reel> personalizedHomeFeed(Long viewerId, int limit) {
        int pageSize = Math.max(1, Math.min(limit, 60));
        // Pull a wider candidate pool than the page size so the ranker
        // has room to actually rerank.
        int poolSize = Math.min(200, pageSize * 5);
        List<Reel> pool = reels.findRecent(PageRequest.of(0, poolSize));
        List<Reel> ranked = rankReels(viewerId, pool);
        return ranked.size() > pageSize
                ? ranked.subList(0, pageSize) : ranked;
    }

    // =====================================================================
    //  INTEREST PROFILE
    // =====================================================================

    public Map<String, Object> interestProfile(Long userId) {
        List<UserInterest> topTopics = interests.topByUser(userId,
                PageRequest.of(0, 20));
        List<CreatorAffinity> topCreators = affinities.topByUser(userId,
                PageRequest.of(0, 20));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("topics", topTopics.stream()
                .map(t -> Map.of("topic", t.getTopic(),
                        "score", round(t.getScore())))
                .toList());
        out.put("creators", topCreators.stream()
                .map(c -> Map.of("creatorId", c.getCreatorId(),
                        "score", round(c.getScore())))
                .toList());
        out.put("model", modelRegistry.active().id());
        out.put("aiOverride", aiService.ranking().isPresent());
        return out;
    }

    private static double round(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    // =====================================================================
    //  PROFILE CONTEXT (cached per request)
    // =====================================================================

    /**
     * Snapshot of everything the ranker needs about the viewer. Loaded
     * once per ranking call so per-item scoring is a pure map lookup.
     */
    private ProfileContext loadProfile(Long viewerId) {
        Map<String, Double> topicScores = interests
                .topByUser(viewerId, PageRequest.of(0, MAX_TOPICS_LOADED))
                .stream()
                .collect(Collectors.toMap(UserInterest::getTopic,
                        UserInterest::getScore));

        Map<Long, Double> creatorScores = affinities
                .topByUser(viewerId, PageRequest.of(0, 100))
                .stream()
                .collect(Collectors.toMap(CreatorAffinity::getCreatorId,
                        CreatorAffinity::getScore));

        Map<Long, Double> creatorQuality = creatorStats
                .findAllById(creatorScores.keySet())
                .stream()
                .collect(Collectors.toMap(CreatorStatsRt::getCreatorId,
                        CreatorStatsRt::getQualityScore));

        Map<Long, Double> trendingReelIds = trending
                .findByTargetTypeOrderByScoreDesc(SignalKinds.T_REEL,
                        PageRequest.of(0, 200))
                .stream()
                .filter(t -> t.getTargetId() != null)
                .collect(Collectors.toMap(TrendingItem::getTargetId,
                        TrendingItem::getScore,
                        (a, b) -> a));

        // Followed creator ids — used for the social-proof feature.
        Set<Long> followedIds;
        try {
            followedIds = new HashSet<>(creatorScores.keySet());
        } catch (Exception e) {
            followedIds = Set.of();
        }

        return new ProfileContext(
                topicScores, creatorScores, creatorQuality,
                trendingReelIds, followedIds, new HashMap<>());
    }

    /** Bag of state the per-item scorer reads. */
    private record ProfileContext(
            Map<String, Double> topicScores,
            Map<Long,   Double> creatorScores,
            Map<Long,   Double> creatorQuality,
            Map<Long,   Double> trendingReelIds,
            Set<Long>           followedIds,
            Map<Long, Long>     creatorHistogram) {}

    @SuppressWarnings("unused")
    private static Instant toInstant(LocalDateTime ts) {
        return ts == null ? Instant.now() : ts.toInstant(ZoneOffset.UTC);
    }
}
