package com.example.demo.controller;

import com.example.demo.dto.ReelDto;
import com.example.demo.dto.UserSummaryDto;
import com.example.demo.entity.Reel;
import com.example.demo.entity.TrendingItem;
import com.example.demo.entity.User;
import com.example.demo.recommendation.SignalKinds;
import com.example.demo.repository.ReelLikeRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.FeedSignalService;
import com.example.demo.service.RecommendationService;
import com.example.demo.service.TrendingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * REST surface for the recommendation engine.
 *
 * <ul>
 *   <li>{@code POST  /recs/signal}                — fire-and-forget signal ingestion</li>
 *   <li>{@code GET   /recs/feed?limit=}          — personalised reel/home feed (replaces or augments the
 *       chronological one)</li>
 *   <li>{@code GET   /recs/trending/{type}}      — top trending items by type
 *       (HASHTAG | CREATOR | REEL | STORY | LIVE) — optional {@code category=}</li>
 *   <li>{@code GET   /recs/suggested/creators}   — "people you may know"</li>
 *   <li>{@code GET   /recs/interests}            — debug snapshot of the user's interest vector</li>
 * </ul>
 */
@RestController
@RequestMapping("/recs")
@RequiredArgsConstructor
public class RecommendationController {

    private final FeedSignalService signals;
    private final RecommendationService recs;
    private final TrendingService trendingService;
    private final UserRepository users;
    private final ReelLikeRepository likes;

    // ============================================================
    //  SIGNAL INGESTION
    // ============================================================

    @PostMapping("/signal")
    public ResponseEntity<Map<String, Object>> ingest(
            @RequestBody SignalBody body, Authentication auth) {
        Long uid = currentUserId(auth);
        if (uid == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }
        signals.record(uid,
                body.kind,
                body.targetType,
                body.targetId,
                body.targetLabel,
                body.creatorId,
                body.weight);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    /** Allows the client to batch a screen-worth of events in one request
     *  — important for low-end devices and shaky connections. */
    @PostMapping("/signal/batch")
    public ResponseEntity<Map<String, Object>> ingestBatch(
            @RequestBody List<SignalBody> batch, Authentication auth) {
        Long uid = currentUserId(auth);
        if (uid == null) {
            return ResponseEntity.status(401).body(Map.of("ok", false));
        }
        if (batch != null) {
            for (SignalBody body : batch) {
                signals.record(uid,
                        body.kind, body.targetType,
                        body.targetId, body.targetLabel,
                        body.creatorId, body.weight);
            }
        }
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "count", batch == null ? 0 : batch.size()));
    }

    // ============================================================
    //  PERSONALISED FEED
    // ============================================================

    @GetMapping("/feed")
    public List<ReelDto> personalFeed(@RequestParam(defaultValue = "20") int limit,
                                       Authentication auth) {
        Long uid = currentUserId(auth);
        List<Reel> ranked = recs.personalizedHomeFeed(uid, limit);
        if (ranked.isEmpty()) return List.of();

        // Hydrate creator + like-state — kept inline to avoid pulling in
        // the ReelService.hydrate private method.
        Set<Long> creatorIds = new HashSet<>();
        for (Reel r : ranked) creatorIds.add(r.getUserId());
        Map<Long, User> creatorMap = new HashMap<>();
        for (User u : users.findAllById(creatorIds)) creatorMap.put(u.getId(), u);

        Set<Long> likedIds = uid == null ? Set.of() :
                Set.copyOf(likes.findLikedIds(uid,
                        ranked.stream().map(Reel::getId).toList()));

        List<ReelDto> dtos = new ArrayList<>(ranked.size());
        for (Reel r : ranked) {
            dtos.add(ReelDto.from(r, creatorMap.get(r.getUserId()),
                    likedIds.contains(r.getId())));
        }
        return dtos;
    }

    // ============================================================
    //  TRENDING
    // ============================================================

    @GetMapping("/trending/{type}")
    public List<TrendingDto> trending(@PathVariable String type,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(defaultValue = "20") int limit) {
        String t = type == null ? "" : type.toUpperCase(Locale.ROOT);
        List<TrendingItem> rows = (category == null || category.isBlank())
                ? trendingService.top(t, limit)
                : trendingService.topByCategory(t, category, limit);
        return rows.stream().map(TrendingDto::from).toList();
    }

    // ============================================================
    //  SUGGESTED CREATORS
    // ============================================================

    @GetMapping("/suggested/creators")
    public List<UserSummaryDto> suggestedCreators(@RequestParam(defaultValue = "12") int limit,
                                                   Authentication auth) {
        Long uid = currentUserId(auth);
        if (uid == null) return List.of();
        List<Long> ids = recs.suggestedCreators(uid, Math.max(1, Math.min(limit, 50)));
        if (ids.isEmpty()) return List.of();
        Map<Long, User> map = new HashMap<>();
        for (User u : users.findAllById(ids)) map.put(u.getId(), u);
        List<UserSummaryDto> out = new ArrayList<>(ids.size());
        for (Long id : ids) {
            User u = map.get(id);
            if (u != null) out.add(UserSummaryDto.from(u));
        }
        return out;
    }

    // ============================================================
    //  INTEREST PROFILE  (debug / future personalisation surface)
    // ============================================================

    @GetMapping("/interests")
    public Map<String, Object> interests(Authentication auth) {
        Long uid = currentUserId(auth);
        if (uid == null) return Map.of();
        return recs.interestProfile(uid);
    }

    // ============================================================
    //  helpers
    // ============================================================

    private Long currentUserId(Authentication auth) {
        if (auth == null) return null;
        return users.findByEmail(auth.getName()).map(User::getId).orElse(null);
    }

    // ============================================================
    //  request / response shapes
    // ============================================================

    /** One inbound signal — every field is optional except {@code kind}
     *  and {@code targetType}. */
    public static class SignalBody {
        public String kind;
        public String targetType;
        public Long   targetId;
        public String targetLabel;
        public Long   creatorId;
        public double weight;

        @SuppressWarnings("unused")
        public static SignalBody of(String kind, String targetType, Long targetId) {
            SignalBody b = new SignalBody();
            b.kind = kind;
            b.targetType = targetType;
            b.targetId = targetId;
            b.weight = SignalKinds.defaultWeight(kind);
            return b;
        }
    }

    public static class TrendingDto {
        public Long id;
        public String targetType;
        public Long targetId;
        public String targetLabel;
        public String category;
        public double score;

        static TrendingDto from(TrendingItem t) {
            TrendingDto d = new TrendingDto();
            d.id          = t.getId();
            d.targetType  = t.getTargetType();
            d.targetId    = t.getTargetId();
            d.targetLabel = t.getTargetLabel();
            d.category    = t.getCategory();
            d.score       = Math.round(t.getScore() * 100.0) / 100.0;
            return d;
        }
    }
}
