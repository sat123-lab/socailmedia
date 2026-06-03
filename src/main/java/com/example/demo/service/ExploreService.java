package com.example.demo.service;

import com.example.demo.dto.ExploreCreatorDto;
import com.example.demo.dto.ExploreHashtagDto;
import com.example.demo.dto.ExploreHomeDto;
import com.example.demo.dto.ExploreMusicDto;
import com.example.demo.dto.ExploreReelDto;
import com.example.demo.dto.ExploreTopicDto;
import com.example.demo.dto.LiveStreamDto;
import com.example.demo.dto.StoryGroupDto;
import com.example.demo.entity.Hashtag;
import com.example.demo.entity.LiveStream;
import com.example.demo.entity.Reel;
import com.example.demo.entity.TrendingItem;
import com.example.demo.entity.User;
import com.example.demo.entity.UserInterest;
import com.example.demo.repository.LiveStreamRepository;
import com.example.demo.repository.ReelRepository;
import com.example.demo.repository.StoryRepository;
import com.example.demo.repository.UserInterestRepository;
import com.example.demo.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Orchestrates the Explore & Discovery Hub.
 *
 * <p>Acts as a fan-out aggregator: the eight Explore sections are each
 * served by an existing service (recommendation, trending, live, story,
 * search, …) — this class composes them in parallel and applies a
 * short Caffeine cache so the busy Explore endpoint scales to thousands
 * of concurrent requests without re-running the same nine queries per
 * user-tap.
 *
 * <p>Why fan-out instead of N round-trips from the client:
 * <ul>
 *   <li>One request beats eight on cellular networks (less radio wake-ups).</li>
 *   <li>The server can short-circuit cold sections to keep TTFB low.</li>
 *   <li>Auth happens once.</li>
 *   <li>The cache key includes the viewer id, so personalisation still
 *       composes with the cache.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ExploreService {

    private final RecommendationService recommendation;
    private final TrendingService trending;
    private final HashtagService hashtags;
    private final LiveStreamService live;
    private final StoryService stories;
    private final ReelRepository reels;
    private final UserRepository users;
    private final UserInterestRepository interests;
    private final LiveStreamRepository liveRepo;
    private final StoryRepository storyRepo;

    /**
     * Bounded executor for parallel section fan-out. A fixed pool of 8
     * keeps the latency floor low without melting the box if a thousand
     * users hit Explore at once.
     */
    private final ExecutorService fanOut =
            Executors.newFixedThreadPool(8, r -> {
                Thread t = new Thread(r, "explore-fanout");
                t.setDaemon(true);
                return t;
            });

    /**
     * 30-second per-(viewer, locale) cache. Short enough that "Trending
     * Reels" feels live, long enough that scrolling away and back is
     * instant. The viewer id is part of the key so personalisation
     * still composes correctly with caching.
     */
    private final Cache<String, ExploreHomeDto> homeCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(30))
            .maximumSize(10_000)
            .build();

    // ========================================================================
    //  HOME — batched 8-section response
    // ========================================================================

    public ExploreHomeDto home(Long viewerId, String region, int sectionLimit) {
        final int limit = sectionLimit <= 0 ? 12 : Math.min(sectionLimit, 30);
        final String key = (viewerId == null ? "anon" : viewerId.toString())
                + "::" + (region == null ? "" : region) + "::" + limit;
        return homeCache.get(key, k -> buildHome(viewerId, region, limit));
    }

    private ExploreHomeDto buildHome(Long viewerId, String region, int limit) {
        // Kick off every section in parallel.
        CompletableFuture<List<ExploreReelDto>> fReels =
                CompletableFuture.supplyAsync(() -> trendingReels(limit), fanOut);
        CompletableFuture<List<ExploreCreatorDto>> fCreators =
                CompletableFuture.supplyAsync(() -> recommendedCreators(viewerId, limit), fanOut);
        CompletableFuture<List<ExploreHashtagDto>> fHashtags =
                CompletableFuture.supplyAsync(() -> trendingHashtags(limit), fanOut);
        CompletableFuture<List<LiveStreamDto>> fLive =
                CompletableFuture.supplyAsync(() -> liveNow(limit), fanOut);
        CompletableFuture<List<StoryGroupDto>> fStories =
                CompletableFuture.supplyAsync(() -> popularStories(viewerId, limit), fanOut);
        CompletableFuture<List<ExploreCreatorDto>> fNewCreators =
                CompletableFuture.supplyAsync(() -> newCreators(limit), fanOut);
        CompletableFuture<List<ExploreHashtagDto>> fLocal =
                CompletableFuture.supplyAsync(() -> localTrends(region, limit), fanOut);
        CompletableFuture<List<ExploreTopicDto>> fTopics =
                CompletableFuture.supplyAsync(() -> suggestedTopics(viewerId, limit), fanOut);
        CompletableFuture<List<ExploreMusicDto>> fMusic =
                CompletableFuture.supplyAsync(() -> trendingMusic(limit), fanOut);

        // Wait for them all — bounded by the slowest section, capped by the
        // executor's thread budget.
        CompletableFuture.allOf(fReels, fCreators, fHashtags, fLive, fStories,
                fNewCreators, fLocal, fTopics, fMusic).join();

        return ExploreHomeDto.builder()
                .trendingReels(get(fReels))
                .recommendedCreators(get(fCreators))
                .trendingHashtags(get(fHashtags))
                .liveNow(get(fLive))
                .popularStories(get(fStories))
                .newCreators(get(fNewCreators))
                .localTrends(get(fLocal))
                .suggestedTopics(get(fTopics))
                .trendingMusic(get(fMusic))
                .generatedAtMs(System.currentTimeMillis())
                .build();
    }

    // ========================================================================
    //  Individual section providers — each is reused by the standalone
    //  endpoints (Trending Center, Creator Discovery) too.
    // ========================================================================

    public List<ExploreReelDto> trendingReels(int limit) {
        List<Reel> rows = reels.findTrending(PageRequest.of(0, Math.max(1, limit)));
        if (rows.isEmpty()) return List.of();
        Map<Long, User> creatorMap = mapUsers(
                rows.stream().map(Reel::getUserId).toList());
        List<ExploreReelDto> out = new ArrayList<>(rows.size());
        for (Reel r : rows) {
            out.add(ExploreReelDto.from(r, creatorMap.get(r.getUserId())));
        }
        return out;
    }

    public List<ExploreCreatorDto> recommendedCreators(Long viewerId, int limit) {
        if (viewerId == null) {
            // Cold start — fall back to verified-creator leaderboard so an
            // anonymous Explore visit still has something compelling.
            return users.findVerifiedCreators(PageRequest.of(0, limit))
                    .stream().map(ExploreCreatorDto::from).toList();
        }
        List<Long> ids = recommendation.suggestedCreators(viewerId, limit);
        if (ids.isEmpty()) {
            return users.findVerifiedCreators(PageRequest.of(0, limit))
                    .stream().map(ExploreCreatorDto::from).toList();
        }
        Map<Long, User> map = mapUsers(ids);
        List<ExploreCreatorDto> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            User u = map.get(id);
            if (u != null) out.add(ExploreCreatorDto.from(u));
        }
        return out;
    }

    public List<ExploreHashtagDto> trendingHashtags(int limit) {
        // Prefer the signal-decayed leaderboard from TrendingService —
        // it weights momentum heavier than raw usage. Fall back to the
        // usage-count list if the trend table hasn't warmed up yet
        // (fresh DB, low traffic).
        List<TrendingItem> top = trending.top("HASHTAG", limit);
        if (!top.isEmpty()) {
            return top.stream()
                    .map(ExploreHashtagDto::fromTrend)
                    .toList();
        }
        return hashtags.trending(limit).stream()
                .map(ExploreHashtagDto::fromHashtag)
                .toList();
    }

    public List<LiveStreamDto> liveNow(int limit) {
        return live.trending(null, limit);
    }

    /**
     * "Popular Stories" — anchored by signal-based trending stories;
     * falls back to the viewer's story feed if the trend table is
     * empty (e.g. fresh DB).
     */
    public List<StoryGroupDto> popularStories(Long viewerId, int limit) {
        List<TrendingItem> top = trending.top("STORY", limit);
        if (!top.isEmpty()) {
            // Hydrate creators referenced by the trending stories.
            Set<Long> creatorIds = new java.util.LinkedHashSet<>();
            for (TrendingItem t : top) {
                if (t.getTargetId() != null) creatorIds.add(t.getTargetId());
            }
            List<StoryGroupDto> out = new ArrayList<>();
            Map<Long, User> userMap = mapUsers(creatorIds.stream().toList());
            for (Long uid : creatorIds) {
                User u = userMap.get(uid);
                if (u == null) continue;
                if (!storyRepo.existsActiveByUser(uid, LocalDateTime.now())) continue;
                out.add(StoryGroupDto.headerOnly(u));
                if (out.size() >= limit) break;
            }
            if (!out.isEmpty()) return out;
        }
        if (viewerId == null) return List.of();
        User viewer = users.findById(viewerId).orElse(null);
        if (viewer == null) return List.of();
        try {
            return stories.getStoryFeed(viewer.getEmail()).stream().limit(limit).toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    public List<ExploreCreatorDto> newCreators(int limit) {
        return users.findNewCreators(PageRequest.of(0, limit))
                .stream().map(ExploreCreatorDto::from).toList();
    }

    /**
     * Local trends — currently region-agnostic (we surface the global
     * signal leaderboard), but the parameter is here so we can ship
     * geo-IP localisation later without an API change.
     */
    public List<ExploreHashtagDto> localTrends(String region, int limit) {
        // When TrendingItem.category is filled with a region code later
        // ("IN", "US", …) we'll swap to `trending.topByCategory`. Today
        // the table doesn't carry geo metadata so we return the global
        // leaderboard — the API shape stays stable.
        return trendingHashtags(limit);
    }

    /**
     * Suggested topics — distilled from the user's interest vector plus
     * the global trend leaderboard. Cold-start users see the global
     * top categories.
     */
    public List<ExploreTopicDto> suggestedTopics(Long viewerId, int limit) {
        // 1. Try the user's interest topics (highest weight first).
        if (viewerId != null) {
            List<UserInterest> top = interests.topByUser(viewerId, PageRequest.of(0, limit));
            if (!top.isEmpty()) {
                return top.stream().map(ExploreTopicDto::fromInterest).toList();
            }
        }
        // 2. Cold start — derive from trending hashtag categories.
        List<TrendingItem> g = trending.top("HASHTAG", limit);
        return g.stream()
                .filter(t -> t.getCategory() != null && !t.getCategory().isBlank())
                .map(t -> new ExploreTopicDto(t.getCategory(), t.getScore()))
                .distinct()
                .limit(limit)
                .toList();
    }

    public List<ExploreMusicDto> trendingMusic(int limit) {
        // Look back at the last 14 days only — anything older isn't
        // really trending.
        LocalDateTime since = LocalDateTime.now().minusDays(14);
        List<Object[]> rows = reels.findTrendingMusic(since, PageRequest.of(0, limit));
        List<ExploreMusicDto> out = new ArrayList<>(rows.size());
        for (Object[] row : rows) {
            out.add(new ExploreMusicDto(
                    (String) row[0],
                    (String) row[1],
                    row[2] == null ? 0L : ((Number) row[2]).longValue(),
                    row[3] == null ? 0.0 : ((Number) row[3]).doubleValue(),
                    (String) row[4]));
        }
        return out;
    }

    // ========================================================================
    //  Creator Discovery — segments behind dedicated endpoints
    // ========================================================================

    public List<ExploreCreatorDto> verifiedCreators(int limit) {
        return users.findVerifiedCreators(PageRequest.of(0, limit))
                .stream().map(ExploreCreatorDto::from).toList();
    }

    public List<ExploreCreatorDto> creatorsByCategory(String category, int limit) {
        if (category == null || category.isBlank()) return List.of();
        return users.findCreatorsByCategory(category, PageRequest.of(0, limit))
                .stream().map(ExploreCreatorDto::from).toList();
    }

    /**
     * "Emerging" creators — users tagged CREATOR with low-to-medium
     * follower count but rising momentum (a non-zero qualityScore from
     * the realtime stats table would be the ideal signal; today we use
     * follower count buckets so the endpoint works on a cold DB).
     */
    public List<ExploreCreatorDto> emergingCreators(int limit) {
        Pageable page = PageRequest.of(0, Math.max(limit * 4, 24));
        List<ExploreCreatorDto> pool = users.findNewCreators(page).stream()
                .filter(u -> u.getFollowersCount() < 5000)
                .filter(u -> u.getFollowersCount() >= 100)
                .map(ExploreCreatorDto::from)
                .toList();
        return pool.size() <= limit ? pool : pool.subList(0, limit);
    }

    /**
     * "Fast-growing" — same pool sorted by follower count desc as a
     * proxy until we wire in a delta-based metric. Bounded so we don't
     * just surface the top accounts.
     */
    public List<ExploreCreatorDto> fastGrowingCreators(int limit) {
        Pageable page = PageRequest.of(0, Math.max(limit * 3, 30));
        List<User> pool = users.findNewCreators(page);
        pool = new ArrayList<>(pool);
        pool.sort(Comparator.comparingInt(User::getFollowersCount).reversed());
        return pool.stream().limit(limit).map(ExploreCreatorDto::from).toList();
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    private Map<Long, User> mapUsers(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return Map.of();
        Map<Long, User> map = new HashMap<>();
        for (User u : users.findAllById(ids)) map.put(u.getId(), u);
        return map;
    }

    private static <T> T get(CompletableFuture<T> f) {
        try {
            return f.join();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Used by the controller's locale-fallback path. Normalises a region
     * string ("in", "IN-tg", "ZZ") to an upper-case 2-char code or null.
     */
    public static String normaliseRegion(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim().toUpperCase(Locale.ROOT);
        if (trimmed.length() < 2) return null;
        return trimmed.substring(0, 2);
    }
}
