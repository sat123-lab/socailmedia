package com.example.demo.service;

import com.example.demo.dto.CreatorStatsDto;
import com.example.demo.entity.User;
import com.example.demo.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.function.Supplier;

/**
 * Backs the Professional Dashboard surfaced to CREATOR / BUSINESS accounts.
 *
 * <p>The rollup is cached for 60 s in {@code user:profile} since it's
 * relatively expensive (multiple aggregate queries) but tolerant of slight
 * staleness on the dashboard.</p>
 */
@Service
@RequiredArgsConstructor
public class CreatorService {

    private final UserRepository users;
    private final PostRepository posts;
    private final ReelRepository reels;
    private final StoryRepository stories;
    private final FollowRepository follows;

    @PersistenceContext
    private EntityManager em;

    @Cacheable(cacheNames = "user:profile", key = "'creator-' + #userId")
    public CreatorStatsDto stats(Long userId) {
        User u = users.findById(userId).orElseThrow(() -> new RuntimeException("User"));

        long postsCount   = scalar(() -> posts.countByUser_Id(userId));
        long reelsCount   = scalar(() -> em.createQuery(
                "SELECT COUNT(r) FROM Reel r WHERE r.userId = :u AND r.deleted = false", Long.class)
                .setParameter("u", userId).getSingleResult());
        long storiesCount = scalar(() -> em.createQuery(
                "SELECT COUNT(s) FROM Story s WHERE s.user.id = :u", Long.class)
                .setParameter("u", userId).getSingleResult());

        long likesOnPosts = scalar(() -> em.createQuery(
                "SELECT COUNT(l) FROM Like l WHERE l.post.user.id = :u", Long.class)
                .setParameter("u", userId).getSingleResult());
        long commentsOnPosts = scalar(() -> em.createQuery(
                "SELECT COUNT(c) FROM Comment c WHERE c.post.user.id = :u", Long.class)
                .setParameter("u", userId).getSingleResult());

        Map<String, Long> reelRollup = reelRollup(userId);
        long reelLikes    = reelRollup.getOrDefault("likes",    0L);
        long reelComments = reelRollup.getOrDefault("comments", 0L);
        long reelShares   = reelRollup.getOrDefault("shares",   0L);
        long reelViews    = reelRollup.getOrDefault("views",    0L);

        long totalLikes    = likesOnPosts + reelLikes;
        long totalComments = commentsOnPosts + reelComments;
        long totalShares   = reelShares;

        double engagement = reelViews > 0
                ? (totalLikes + totalComments) * 100.0 / reelViews
                : 0.0;

        long followers = scalar(() -> follows.countByFollowing(u));
        long following = scalar(() -> follows.countByFollower(u));

        return CreatorStatsDto.builder()
                .userId(userId)
                .accountType(u.getAccountType())
                .verified(u.isVerified())
                .postsCount(postsCount)
                .reelsCount(reelsCount)
                .storiesCount(storiesCount)
                .totalLikes(totalLikes)
                .totalComments(totalComments)
                .totalShares(totalShares)
                .totalReelViews(reelViews)
                .followers(followers)
                .following(following)
                .engagementRate(Math.round(engagement * 100.0) / 100.0)
                .build();
    }

    @Transactional
    @CacheEvict(cacheNames = "user:profile", key = "'creator-' + #userId")
    public User updateAccountType(Long userId, String type, String bio,
                                  String website, String category) {
        User u = users.findById(userId).orElseThrow(() -> new RuntimeException("User"));
        if (type != null && !type.isBlank()) {
            String upper = type.toUpperCase();
            if (!upper.equals("PERSONAL") && !upper.equals("CREATOR") && !upper.equals("BUSINESS")) {
                throw new IllegalArgumentException("Bad account type");
            }
            u.setAccountType(upper);
        }
        if (bio != null) u.setBio(bio.trim());
        if (website != null) u.setWebsite(website.trim());
        if (category != null) u.setCategory(category.trim());
        return users.save(u);
    }

    @Transactional
    @CacheEvict(cacheNames = "user:profile", key = "'creator-' + #userId")
    public User setVerified(Long userId, boolean verified) {
        User u = users.findById(userId).orElseThrow(() -> new RuntimeException("User"));
        u.setVerified(verified);
        return users.save(u);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Long> reelRollup(Long userId) {
        Object[] row = (Object[]) em.createNativeQuery(
                "SELECT COALESCE(SUM(likes_count),0)    AS lk, " +
                "       COALESCE(SUM(comments_count),0) AS cm, " +
                "       COALESCE(SUM(shares_count),0)   AS sh, " +
                "       COALESCE(SUM(views_count),0)    AS vw " +
                "FROM reels WHERE user_id = ?1 AND deleted = 0")
                .setParameter(1, userId)
                .getSingleResult();
        return Map.of(
                "likes",    longValue(row[0]),
                "comments", longValue(row[1]),
                "shares",   longValue(row[2]),
                "views",    longValue(row[3])
        );
    }

    private static long longValue(Object o) {
        if (o == null) return 0L;
        if (o instanceof Number n) return n.longValue();
        return Long.parseLong(o.toString());
    }

    private static long scalar(Supplier<Number> q) {
        try { return q.get().longValue(); } catch (Exception e) { return 0L; }
    }
}
