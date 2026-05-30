package com.example.demo.controller;

import com.example.demo.dto.ExploreCreatorDto;
import com.example.demo.dto.ExploreHashtagDto;
import com.example.demo.dto.ExploreHomeDto;
import com.example.demo.dto.ExploreMusicDto;
import com.example.demo.dto.ExploreReelDto;
import com.example.demo.dto.ExploreTopicDto;
import com.example.demo.dto.LiveStreamDto;
import com.example.demo.dto.StoryGroupDto;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ExploreService;
import com.example.demo.service.FeedSignalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST surface for the Explore & Discovery Hub.
 *
 * <p>Endpoints (all paginate via {@code limit} except the batched home):
 * <ul>
 *   <li>{@code GET  /explore/home}              — eight-section batched payload</li>
 *   <li>{@code GET  /explore/trending/reels}    — Trending Reels rail (paginated via existing reels API recommended for infinite scroll)</li>
 *   <li>{@code GET  /explore/trending/hashtags} — Trending hashtags (signal-decayed → usage fallback)</li>
 *   <li>{@code GET  /explore/trending/music}    — Trending audio tracks aggregated from reels</li>
 *   <li>{@code GET  /explore/live}              — Currently broadcasting live</li>
 *   <li>{@code GET  /explore/stories/popular}   — Popular stories rail</li>
 *   <li>{@code GET  /explore/creators/recommended} — Personalised suggestions</li>
 *   <li>{@code GET  /explore/creators/new}      — Newly joined creator accounts</li>
 *   <li>{@code GET  /explore/creators/emerging} — Mid-tier rising creators</li>
 *   <li>{@code GET  /explore/creators/fast-growing} — Accelerating-growth creators</li>
 *   <li>{@code GET  /explore/creators/verified} — Verified creator leaderboard</li>
 *   <li>{@code GET  /explore/creators/category/{c}} — Creators filtered by category</li>
 *   <li>{@code GET  /explore/topics/suggested}  — Topic suggestions from the interest graph</li>
 *   <li>{@code POST /explore/analytics/batch}   — Impression / CTR ingest</li>
 * </ul>
 *
 * <p>Nothing here is auth-required at the controller level — the
 * underlying services degrade gracefully when {@code viewer} is null,
 * so anonymous (logged-out) Explore visits still get a compelling page.
 * Where personalisation matters (recommended creators, suggested
 * topics) the controller passes through the authenticated viewer id;
 * otherwise the cache key is "anon" and every anonymous request hits
 * the same cached payload.
 */
@RestController
@RequestMapping("/explore")
@RequiredArgsConstructor
public class ExploreController {

    private final ExploreService explore;
    private final UserRepository users;
    private final FeedSignalService signals;

    // ========================================================================
    //  HOME — batched payload
    // ========================================================================

    @GetMapping("/home")
    public ExploreHomeDto home(@RequestParam(defaultValue = "12") int limit,
                                @RequestParam(required = false) String region,
                                Authentication auth) {
        Long viewerId = currentUserId(auth);
        String normalised = ExploreService.normaliseRegion(region);
        return explore.home(viewerId, normalised, limit);
    }

    // ========================================================================
    //  TRENDING — paginated rails
    // ========================================================================

    @GetMapping("/trending/reels")
    public List<ExploreReelDto> trendingReels(@RequestParam(defaultValue = "24") int limit) {
        return explore.trendingReels(limit);
    }

    @GetMapping("/trending/hashtags")
    public List<ExploreHashtagDto> trendingHashtags(@RequestParam(defaultValue = "30") int limit) {
        return explore.trendingHashtags(limit);
    }

    @GetMapping("/trending/music")
    public List<ExploreMusicDto> trendingMusic(@RequestParam(defaultValue = "20") int limit) {
        return explore.trendingMusic(limit);
    }

    @GetMapping("/live")
    public List<LiveStreamDto> liveNow(@RequestParam(defaultValue = "24") int limit) {
        return explore.liveNow(limit);
    }

    @GetMapping("/stories/popular")
    public List<StoryGroupDto> popularStories(@RequestParam(defaultValue = "20") int limit,
                                               Authentication auth) {
        return explore.popularStories(currentUserId(auth), limit);
    }

    @GetMapping("/topics/suggested")
    public List<ExploreTopicDto> suggestedTopics(@RequestParam(defaultValue = "20") int limit,
                                                  Authentication auth) {
        return explore.suggestedTopics(currentUserId(auth), limit);
    }

    // ========================================================================
    //  CREATOR DISCOVERY
    // ========================================================================

    @GetMapping("/creators/recommended")
    public List<ExploreCreatorDto> recommendedCreators(
            @RequestParam(defaultValue = "20") int limit, Authentication auth) {
        return explore.recommendedCreators(currentUserId(auth), limit);
    }

    @GetMapping("/creators/new")
    public List<ExploreCreatorDto> newCreators(
            @RequestParam(defaultValue = "20") int limit) {
        return explore.newCreators(limit);
    }

    @GetMapping("/creators/emerging")
    public List<ExploreCreatorDto> emergingCreators(
            @RequestParam(defaultValue = "20") int limit) {
        return explore.emergingCreators(limit);
    }

    @GetMapping("/creators/fast-growing")
    public List<ExploreCreatorDto> fastGrowingCreators(
            @RequestParam(defaultValue = "20") int limit) {
        return explore.fastGrowingCreators(limit);
    }

    @GetMapping("/creators/verified")
    public List<ExploreCreatorDto> verifiedCreators(
            @RequestParam(defaultValue = "20") int limit) {
        return explore.verifiedCreators(limit);
    }

    @GetMapping("/creators/category/{category}")
    public List<ExploreCreatorDto> creatorsByCategory(
            @PathVariable String category,
            @RequestParam(defaultValue = "20") int limit) {
        return explore.creatorsByCategory(category, limit);
    }

    // ========================================================================
    //  ANALYTICS — explore impressions / CTR
    // ========================================================================

    /**
     * Batched impression + click ingest. Designed to be called once per
     * scroll-stop (or app background) — never per-frame.
     *
     * <p>Lives on top of {@link FeedSignalService} so we don't introduce
     * a new pipeline; every explore event becomes a feed signal with a
     * stable kind prefix ("explore.impression", "explore.click"), which
     * also means the recommendation engine learns from the explore
     * funnel for free.
     */
    @PostMapping("/analytics/batch")
    public ResponseEntity<Map<String, Object>> ingestAnalytics(
            @RequestBody List<ExploreEvent> batch, Authentication auth) {
        Long uid = currentUserId(auth);
        if (uid == null) {
            // Anonymous events are dropped — we don't want to bloat the
            // signal stream with un-attributable rows.
            return ResponseEntity.ok(Map.of("ok", true, "count", 0));
        }
        if (batch == null || batch.isEmpty()) {
            return ResponseEntity.ok(Map.of("ok", true, "count", 0));
        }
        int ingested = 0;
        for (ExploreEvent ev : batch) {
            if (ev == null || ev.targetType == null) continue;
            String kind = "explore." + (ev.kind == null ? "impression" : ev.kind);
            double weight = ev.weight > 0 ? ev.weight :
                    (kind.endsWith("click") ? 1.0 : 0.05);
            signals.record(uid, kind, ev.targetType,
                    ev.targetId, ev.targetLabel, ev.creatorId, weight);
            ingested++;
        }
        return ResponseEntity.ok(Map.of("ok", true, "count", ingested));
    }

    // ========================================================================
    //  helpers
    // ========================================================================

    private Long currentUserId(Authentication auth) {
        if (auth == null) return null;
        return users.findByEmail(auth.getName()).map(User::getId).orElse(null);
    }

    /** Minimal request shape for the batched analytics ingest. */
    public static class ExploreEvent {
        /** "impression" or "click" — case-insensitive, no prefix needed. */
        public String kind;
        public String targetType; // REEL · CREATOR · HASHTAG · LIVE · STORY · MUSIC · TOPIC
        public Long targetId;
        public String targetLabel;
        public Long creatorId;
        public double weight;
    }
}
