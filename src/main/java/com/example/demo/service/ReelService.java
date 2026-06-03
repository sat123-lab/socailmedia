package com.example.demo.service;

import com.example.demo.dto.CursorPage;
import com.example.demo.dto.ReelDto;
import com.example.demo.entity.Reel;
import com.example.demo.entity.ReelComment;
import com.example.demo.entity.ReelLike;
import com.example.demo.entity.ReelView;
import com.example.demo.entity.User;
import com.example.demo.repository.ReelCommentRepository;
import com.example.demo.recommendation.SignalKinds;
import com.example.demo.repository.ReelLikeRepository;
import com.example.demo.repository.ReelRepository;
import com.example.demo.repository.ReelViewRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reels engine — feed delivery, like/comment/share toggles, watch-time
 * tracking, and the periodic trending-score recompute.
 *
 * <h3>Trending score</h3>
 * <pre>
 *   score = w_likes·likes + w_comments·comments + w_shares·shares
 *         + w_watch·watchTime — w_age·ageHours
 * </pre>
 * Recomputed every 5 minutes on the freshest 5k reels — that's enough for
 * the feed to feel fresh without thrashing the DB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReelService {

    private final ReelRepository reels;
    private final ReelLikeRepository likes;
    private final ReelCommentRepository comments;
    private final ReelViewRepository views;
    private final UserRepository users;
    private final ContentModerationService moderation;
    private final HashtagService hashtags;
    private final RealtimeEventService realtime;

    /**
     * Optional — wired via setter so this service stays loadable even
     * if the recommendation module is excluded at build time. When the
     * recommender is present the {@link #feed} path personalises the
     * candidate list via {@link RecommendationService#rankReels}; when
     * absent the chronological/trending sort is used as today.
     */
    @Autowired(required = false)
    private RecommendationService recommendations;

    @Autowired(required = false)
    private FeedSignalService signalService;

    // ============================================================
    //  Feed
    // ============================================================
    @Cacheable(cacheNames = "feed:reels",
               key = "T(java.util.Objects).hash(#cursor, #limit, #ranking, #viewer)",
               unless = "#result == null || #result.items.isEmpty()")
    public CursorPage<ReelDto> feed(String cursor, int limit, String ranking, Long viewer) {
        int pageSize = Math.min(Math.max(limit, 1), 30);
        LocalDateTime cursorTs = decodeCursor(cursor);

        // FOR_YOU (default) personalises through the recommender when a
        // viewer is authenticated and the module is available; FOLLOWING /
        // TRENDING keep their original behavior.
        boolean personalize = (ranking == null || "FOR_YOU".equalsIgnoreCase(ranking))
                && viewer != null
                && recommendations != null;

        List<Reel> page;
        if (personalize) {
            // Pull a candidate pool wider than one page so the ranker
            // has room to actually rerank meaningfully.
            int poolSize = Math.min(120, (pageSize + 1) * 4);
            List<Reel> pool = reels.findRecentWithCursor(cursorTs, PageRequest.of(0, poolSize));
            page = recommendations.rankReels(viewer, pool);
            // Truncate after ranking — keep one extra for the hasMore probe.
            if (page.size() > pageSize + 1) page = page.subList(0, pageSize + 1);
        } else if ("TRENDING".equalsIgnoreCase(ranking)) {
            page = reels.findTrendingWithCursor(cursorTs, PageRequest.of(0, pageSize + 1));
        } else {
            page = reels.findRecentWithCursor(cursorTs, PageRequest.of(0, pageSize + 1));
        }

        boolean hasMore = page.size() > pageSize;
        if (hasMore) page = page.subList(0, pageSize);
        return hydrate(page, viewer, hasMore);
    }

    public CursorPage<ReelDto> userFeed(Long userId, String cursor, int limit, Long viewer) {
        int pageSize = Math.min(Math.max(limit, 1), 30);
        List<Reel> page = reels.findByUser(userId, PageRequest.of(0, pageSize));
        return hydrate(page, viewer, false);
    }

    private CursorPage<ReelDto> hydrate(List<Reel> page, Long viewer, boolean hasMore) {
        if (page.isEmpty()) return CursorPage.of(List.of(), null);

        Set<Long> creatorIds = page.stream().map(Reel::getUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = users.findAllById(creatorIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        Set<Long> likedIds = (viewer == null) ? Set.of() :
                Set.copyOf(likes.findLikedIds(viewer, page.stream().map(Reel::getId).toList()));

        List<ReelDto> dtos = page.stream()
                .map(r -> ReelDto.from(r, userMap.get(r.getUserId()), likedIds.contains(r.getId())))
                .toList();

        LocalDateTime last = page.get(page.size() - 1).getCreatedAt();
        return CursorPage.of(dtos, hasMore ? encodeCursor(last) : null);
    }

    // ============================================================
    //  Create / delete
    // ============================================================
    @Transactional
    @CacheEvict(cacheNames = {"feed:reels", "reels:trending"}, allEntries = true)
    public ReelDto create(Long userId, String caption, String videoUrl, String thumbnailUrl,
                          String audioUrl, String audioTitle,
                          int durationSeconds, Integer w, Integer h,
                          String visibility) {
        return create(userId, caption, videoUrl, thumbnailUrl, audioUrl, audioTitle,
                durationSeconds, w, h, visibility, null);
    }

    /**
     * Reels-Editor-aware overload. {@code overlaysJson} is an opaque blob
     * generated by the client editor (text overlays, sticker positions,
     * music timing, transitions, cover metadata). The server stores it
     * verbatim — the player is the only consumer.
     */
    public ReelDto create(Long userId, String caption, String videoUrl, String thumbnailUrl,
                          String audioUrl, String audioTitle,
                          int durationSeconds, Integer w, Integer h,
                          String visibility, String overlaysJson) {
        User user = users.findById(userId).orElseThrow(() -> new RuntimeException("User"));
        if (caption != null) moderation.assertAllowed(caption);

        String extracted = hashtags.extractAndRecord(caption, "REEL", null);
        Reel reel = Reel.builder()
                .userId(userId)
                .caption(caption)
                .videoUrl(videoUrl)
                .thumbnailUrl(thumbnailUrl)
                .audioUrl(audioUrl)
                .audioTitle(audioTitle)
                .durationSeconds(Math.max(durationSeconds, 0))
                .width(w).height(h)
                .visibility(visibility == null ? "PUBLIC" : visibility)
                .hashtags(extracted)
                .overlaysJson(overlaysJson)
                .build();
        Reel saved = reels.save(reel);
        // Now that we have the id, attach hashtag usage rows.
        hashtags.recordEntityRefs(extracted, "REEL", saved.getId());
        return ReelDto.from(saved, user, false);
    }

    @Transactional
    @CacheEvict(cacheNames = {"feed:reels", "reels:trending"}, allEntries = true)
    public void delete(Long reelId, Long actorId, boolean isAdmin) {
        Reel r = reels.findById(reelId).orElseThrow(() -> new RuntimeException("Reel"));
        if (!isAdmin && !r.getUserId().equals(actorId)) {
            throw new SecurityException("Not your reel.");
        }
        r.setDeleted(true);
        reels.save(r);
    }

    // ============================================================
    //  Engagement toggles
    // ============================================================
    @Transactional
    public boolean toggleLike(Long reelId, Long userId) {
        var existing = likes.findByReelIdAndUserId(reelId, userId);
        if (existing.isPresent()) {
            likes.deleteByReelAndUser(reelId, userId);
            reels.bumpLikes(reelId, -1);
            return false;
        }
        likes.save(ReelLike.builder().reelId(reelId).userId(userId).build());
        reels.bumpLikes(reelId, 1);
        emitSignal(userId, SignalKinds.LIKE, reelId);
        return true;
    }

    @Transactional
    public ReelComment addComment(Long reelId, Long userId, String text) {
        if (text == null || text.isBlank()) throw new RuntimeException("Empty comment");
        moderation.assertAllowed(text);
        ReelComment c = comments.save(ReelComment.builder()
                .reelId(reelId).userId(userId).text(text.trim()).build());
        reels.bumpComments(reelId, 1);
        emitSignal(userId, SignalKinds.COMMENT, reelId);
        return c;
    }

    @Transactional
    public void share(Long reelId) {
        reels.bumpShares(reelId);
        // No user context here (shareable link); the client-side signal
        // covers the cases where a user identity is available.
    }

    @Transactional
    public void recordView(Long reelId, Long userId, int watchMs, boolean completed) {
        views.save(ReelView.builder()
                .reelId(reelId).userId(userId)
                .watchMs(Math.max(0, watchMs))
                .completed(completed).build());
        reels.bumpViews(reelId, Math.max(0, watchMs / 1000));
        // Two signals: a baseline VIEW + a stronger COMPLETE_REEL when
        // the viewer actually watched to the end (TikTok's #1 signal).
        if (userId != null) {
            emitSignal(userId, SignalKinds.VIEW, reelId);
            if (completed) emitSignal(userId, SignalKinds.COMPLETE_REEL, reelId);
        }
    }

    /** Best-effort recommendation signal hook — invisible when the
     *  recommender module isn't on the classpath. */
    private void emitSignal(Long userId, String kind, Long reelId) {
        if (signalService == null || userId == null || reelId == null) return;
        Reel r = reels.findById(reelId).orElse(null);
        Long creator = r == null ? null : r.getUserId();
        signalService.record(userId, kind, SignalKinds.T_REEL,
                reelId, null, creator, 0);
    }

    public List<ReelComment> listComments(Long reelId, int limit) {
        int n = Math.min(Math.max(limit, 1), 100);
        return comments.findByReelIdOrderByCreatedAtDesc(reelId, PageRequest.of(0, n));
    }

    // ============================================================
    //  Trending score job
    // ============================================================
    @Scheduled(fixedDelay = 5 * 60 * 1000L)
    @Transactional
    public void recomputeTrendingScores() {
        try {
            List<Reel> recent = reels.findRecent(PageRequest.of(0, 5000));
            long nowEpoch = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);
            for (Reel r : recent) {
                double ageHours = Math.max(1.0,
                        (nowEpoch - r.getCreatedAt().toEpochSecond(ZoneOffset.UTC)) / 3600.0);
                double score =
                        3.0 * r.getLikesCount()
                      + 6.0 * r.getCommentsCount()
                      + 8.0 * r.getSharesCount()
                      + 0.4 * (r.getWatchTimeSeconds() / 60.0)
                      + 1.0 * Math.log1p(r.getViewsCount())
                      - 2.0 * Math.log1p(ageHours);
                reels.setScore(r.getId(), score);
            }
            log.info("Reels trending score recomputed for {} rows.", recent.size());
        } catch (Exception e) {
            log.warn("Trending recompute failed: {}", e.getMessage());
        }
    }

    // ============================================================
    //  Cursor encoding — opaque to clients
    // ============================================================
    static String encodeCursor(LocalDateTime ts) {
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(ts.toString().getBytes(StandardCharsets.UTF_8));
    }

    static LocalDateTime decodeCursor(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            String raw = new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8);
            return LocalDateTime.parse(raw);
        } catch (Exception e) {
            return null;
        }
    }

    // Used by tests / metrics
    public Map<String, Object> stats() {
        Map<String, Object> m = new HashMap<>();
        m.put("reels", reels.count());
        m.put("uptime", Duration.ofMillis(System.currentTimeMillis()).toString());
        return m;
    }
}
