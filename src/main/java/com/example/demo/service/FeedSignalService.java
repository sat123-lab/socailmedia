package com.example.demo.service;

import com.example.demo.entity.FeedSignal;
import com.example.demo.recommendation.SignalKinds;
import com.example.demo.repository.FeedSignalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Single entry point for every "user did something" interaction event.
 *
 * <p>Call sites do <i>not</i> need to know about ranking, trending, or
 * interest graphs — they just record the event here and the roll-up
 * jobs read this log on their own schedule.</p>
 *
 * <p>Writes are async + best-effort: a failure in this service never
 * blocks the original interaction (a like still goes through even if
 * the recommendation pipeline is having a bad day).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedSignalService {

    private final FeedSignalRepository repo;
    /** Lightweight monotonic counter so debug logs read like a timeline. */
    private final AtomicLong ingested = new AtomicLong();

    /**
     * Records one signal. Returns immediately on the caller thread —
     * the actual INSERT runs on the {@code @Async} executor configured
     * for the application.
     *
     * @param userId      who did the thing
     * @param kind        one of {@link SignalKinds}
     * @param targetType  {@code POST | REEL | STORY | LIVE | USER | HASHTAG}
     * @param targetId    DB id of the target (nullable for HASHTAG)
     * @param targetLabel free-form label (used for HASHTAG)
     * @param creatorId   author of the target, if known
     * @param weight      override weight (use ≤ 0 to take the SignalKinds default)
     */
    @Async
    public void record(Long userId, String kind, String targetType,
                       Long targetId, String targetLabel,
                       Long creatorId, double weight) {
        if (userId == null || kind == null || targetType == null) return;
        try {
            double w = weight > 0 ? weight : SignalKinds.defaultWeight(kind);
            FeedSignal s = FeedSignal.builder()
                    .userId(userId)
                    .kind(kind)
                    .targetType(targetType)
                    .targetId(targetId)
                    .targetLabel(targetLabel)
                    .creatorId(creatorId)
                    .weight(w)
                    .createdAt(Instant.now())
                    .build();
            persistInTx(s);
            long n = ingested.incrementAndGet();
            if (n % 1000 == 0) {
                log.info("FeedSignal ingested {} events so far", n);
            }
        } catch (Exception e) {
            // Recommendations are an enhancement layer — never let them
            // surface as user-visible errors.
            log.warn("FeedSignal.record failed for user={} kind={}: {}",
                    userId, kind, e.getMessage());
        }
    }

    /** Convenience overload — pass a positive {@code weight} or 0 for default. */
    public void record(Long userId, String kind, String targetType, Long targetId) {
        record(userId, kind, targetType, targetId, null, null, 0);
    }

    @Transactional
    void persistInTx(FeedSignal s) {
        repo.save(s);
    }

    public long ingestedSoFar() {
        return ingested.get();
    }
}
