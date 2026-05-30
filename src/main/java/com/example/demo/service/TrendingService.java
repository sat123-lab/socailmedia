package com.example.demo.service;

import com.example.demo.entity.FeedSignal;
import com.example.demo.entity.TrendingItem;
import com.example.demo.recommendation.SignalKinds;
import com.example.demo.repository.FeedSignalRepository;
import com.example.demo.repository.TrendingItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Realtime trending engine.
 *
 * <p>Every minute we:</p>
 * <ol>
 *   <li>Decay all existing scores in {@code trending_items} (12-hour
 *       half-life) so yesterday's viral content drops off naturally.</li>
 *   <li>Sum up weighted signals from the last 30 minutes per
 *       (target_type, target_id) pair and add to the decayed score.</li>
 *   <li>Purge anything that decayed below the relevance floor.</li>
 * </ol>
 *
 * <p>Queries serve trending lists with a single indexed read — they
 * never touch the raw signal log.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TrendingService {

    private static final double HALF_LIFE_HOURS = 12;
    private static final long WINDOW_MINUTES = 30;
    private static final double SCORE_FLOOR = 0.01;

    private final FeedSignalRepository signals;
    private final TrendingItemRepository trending;

    @Scheduled(fixedDelay = 60_000)
    public void recomputeTick() {
        try {
            recompute();
        } catch (Exception e) {
            log.warn("Trending recompute failed: {}", e.getMessage());
        }
    }

    @Transactional
    public void recompute() {
        // 1. Decay everything.
        Instant now = Instant.now();
        double decay = Math.pow(0.5, 1.0 / 60.0 / HALF_LIFE_HOURS);
        trending.decayAll(decay, now);

        // 2. Roll up recent signals by (type, id, label).
        Instant since = now.minus(WINDOW_MINUTES, ChronoUnit.MINUTES);
        Map<Key, Double> delta = new HashMap<>();
        for (String type : List.of(SignalKinds.T_HASHTAG, SignalKinds.T_REEL,
                                   SignalKinds.T_LIVE,    SignalKinds.T_STORY,
                                   SignalKinds.T_USER)) {
            for (FeedSignal s : signals.recentByType(type, since)) {
                Key k = new Key(type, s.getTargetId(), s.getTargetLabel());
                delta.merge(k, s.getWeight(), Double::sum);
            }
        }

        // 3. Add the delta back.
        for (Map.Entry<Key, Double> e : delta.entrySet()) {
            Key k = e.getKey();
            TrendingItem row = trending
                    .findByTargetTypeAndTargetIdAndTargetLabel(
                            k.type, k.id, k.label)
                    .orElseGet(() -> TrendingItem.builder()
                            .targetType(k.type)
                            .targetId(k.id)
                            .targetLabel(k.label)
                            .score(0.0)
                            .updatedAt(now)
                            .build());
            row.setScore(Math.max(0, row.getScore() + e.getValue()));
            row.setUpdatedAt(now);
            trending.save(row);
        }

        // 4. Sweep.
        int purged = trending.purgeBelow(SCORE_FLOOR);
        if (purged > 0) log.debug("Trending purged {} items below floor", purged);
    }

    // -------------------------------------------------------------
    //  Reads
    // -------------------------------------------------------------

    public List<TrendingItem> top(String targetType, int limit) {
        return trending.findByTargetTypeOrderByScoreDesc(
                targetType, PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
    }

    public List<TrendingItem> topByCategory(String targetType, String category, int limit) {
        return trending.findByTargetTypeAndCategoryOrderByScoreDesc(
                targetType, category, PageRequest.of(0, Math.max(1, Math.min(limit, 100))));
    }

    private record Key(String type, Long id, String label) {}
}
