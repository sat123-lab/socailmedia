package com.example.demo.service;

import com.example.demo.entity.CreatorAffinity;
import com.example.demo.entity.FeedSignal;
import com.example.demo.entity.UserInterest;
import com.example.demo.recommendation.SignalKinds;
import com.example.demo.repository.CreatorAffinityRepository;
import com.example.demo.repository.FeedSignalRepository;
import com.example.demo.repository.UserInterestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The "training" half of the recommendation engine.
 *
 * <p>Reads new rows from {@link FeedSignalRepository} every minute and
 * rolls them into:</p>
 * <ul>
 *   <li>{@link UserInterest} — user → topic affinity (hashtag / category).</li>
 *   <li>{@link CreatorAffinity} — user → creator affinity.</li>
 * </ul>
 *
 * <p>Existing scores are decayed by a half-life of 7 days on every tick
 * so a user's recent interests dominate older ones without ever fully
 * erasing them.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InterestGraphService {

    /** Half-life of stored affinity scores between roll-up ticks. */
    private static final double HALF_LIFE_HOURS = 168;  // 7 days
    /** Window we read each tick — overlaps the previous a touch for safety. */
    private static final long WINDOW_MINUTES = 5;
    /** Floor we delete below. */
    private static final double SCORE_FLOOR = 0.001;

    private final FeedSignalRepository signals;
    private final UserInterestRepository interests;
    private final CreatorAffinityRepository affinities;

    /** Last Instant we've reconciled up to. Atomic so the scheduler isn't
     *  surprised by overlapping ticks under load. */
    private final AtomicReference<Instant> watermark =
            new AtomicReference<>(Instant.now().minusSeconds(WINDOW_MINUTES * 60));

    @Scheduled(fixedDelay = 60_000)
    public void rollupTick() {
        try {
            doRollup();
        } catch (Exception e) {
            log.warn("InterestGraph rollup tick failed: {}", e.getMessage());
        }
    }

    @Transactional
    public void doRollup() {
        Instant since = watermark.get();
        Instant now = Instant.now();
        List<FeedSignal> batch = signals.findSinceOrderById(since);
        if (batch.isEmpty()) {
            watermark.set(now);
            return;
        }
        log.debug("InterestGraph rolling {} signals from {}", batch.size(), since);

        // Aggregate this batch first so we minimise DB round-trips.
        Map<TopicKey, Double> topicDelta = new HashMap<>();
        Map<CreatorKey, Double> creatorDelta = new HashMap<>();

        for (FeedSignal s : batch) {
            double w = s.getWeight();
            // Topic axis — split hashtags + use targetLabel for HASHTAG events.
            if (SignalKinds.T_HASHTAG.equals(s.getTargetType()) && s.getTargetLabel() != null) {
                bump(topicDelta, new TopicKey(s.getUserId(),
                        normaliseTopic(s.getTargetLabel())), w);
            }

            // Creator axis — we have a creator_id denormalized on the row.
            Long cid = s.getCreatorId();
            if (cid != null && !cid.equals(s.getUserId())) {
                bump(creatorDelta, new CreatorKey(s.getUserId(), cid), w);
            }
            if (SignalKinds.T_USER.equals(s.getTargetType()) && s.getTargetId() != null
                    && !s.getTargetId().equals(s.getUserId())) {
                bump(creatorDelta, new CreatorKey(s.getUserId(), s.getTargetId()), w);
            }
        }

        // Decay existing scores (the same proportional shrink for every row),
        // then add the new batch. We do this in two passes so we only load
        // rows we'll actually touch.
        double decay = decayFactor(WINDOW_MINUTES);
        applyTopicDelta(topicDelta, decay, now);
        applyCreatorDelta(creatorDelta, decay, now);
        watermark.set(now);
    }

    // -------------------------------------------------------------
    //  Implementation details
    // -------------------------------------------------------------

    private void applyTopicDelta(Map<TopicKey, Double> deltas, double decay, Instant now) {
        for (Map.Entry<TopicKey, Double> e : deltas.entrySet()) {
            TopicKey k = e.getKey();
            UserInterest row = interests.findByUserIdAndTopic(k.userId, k.topic)
                    .orElseGet(() -> UserInterest.builder()
                            .userId(k.userId)
                            .topic(k.topic)
                            .score(0.0)
                            .updatedAt(now)
                            .build());
            row.setScore(Math.max(0, row.getScore() * decay + e.getValue()));
            row.setUpdatedAt(now);
            if (row.getScore() < SCORE_FLOOR) {
                if (row.getId() != null) interests.delete(row);
            } else {
                interests.save(row);
            }
        }
    }

    private void applyCreatorDelta(Map<CreatorKey, Double> deltas, double decay, Instant now) {
        for (Map.Entry<CreatorKey, Double> e : deltas.entrySet()) {
            CreatorKey k = e.getKey();
            CreatorAffinity row = affinities.findByUserIdAndCreatorId(k.userId, k.creatorId)
                    .orElseGet(() -> CreatorAffinity.builder()
                            .userId(k.userId)
                            .creatorId(k.creatorId)
                            .score(0.0)
                            .lastSignal(now)
                            .build());
            row.setScore(Math.max(0, row.getScore() * decay + e.getValue()));
            row.setLastSignal(now);
            if (row.getScore() < SCORE_FLOOR) {
                if (row.getId() != null) affinities.delete(row);
            } else {
                affinities.save(row);
            }
        }
    }

    /** Half-life decay applied per tick. */
    private static double decayFactor(long minutesWindow) {
        return Math.pow(0.5, minutesWindow / 60.0 / HALF_LIFE_HOURS);
    }

    private static <K> void bump(Map<K, Double> map, K key, double delta) {
        map.merge(key, delta, Double::sum);
    }

    /** Lowercases + strips leading '#'. Keeps topics roll-up-stable across
     *  case-variant hashtags ("#Sunset", "sunset"). */
    static String normaliseTopic(String raw) {
        String s = raw.trim().toLowerCase(Locale.ROOT);
        return s.startsWith("#") ? s.substring(1) : s;
    }

    private record TopicKey(Long userId, String topic) {}
    private record CreatorKey(Long userId, Long creatorId) {}
}
