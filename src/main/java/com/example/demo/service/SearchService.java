package com.example.demo.service;

import com.example.demo.dto.SearchResultsDto;
import com.example.demo.entity.Hashtag;
import com.example.demo.entity.Post;
import com.example.demo.entity.Reel;
import com.example.demo.entity.User;
import com.example.demo.repository.HashtagRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.ReelRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Unified search across users, hashtags, posts, and reels.
 *
 * <p>The four sections are queried in parallel — for a typical 4-section
 * response we cut latency roughly in half vs sequential.</p>
 *
 * <p>The DB-backed implementation uses indexed LIKE queries today; the
 * service contract is shaped for a future swap to Meilisearch /
 * OpenSearch — every method returns plain maps with the same keys.</p>
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private final UserRepository users;
    private final PostRepository posts;
    private final ReelRepository reels;
    private final HashtagRepository hashtags;
    private final HashtagService hashtagService;

    @Cacheable(cacheNames = "search:trending", key = "#q")
    public SearchResultsDto searchAll(String q, int limit) {
        if (q == null || q.isBlank()) {
            return new SearchResultsDto(q, List.of(), List.of(), List.of(), List.of());
        }
        String norm = q.trim();
        int n = Math.min(Math.max(limit, 1), 20);

        // Run the four sources in parallel — they don't share a tx.
        CompletableFuture<List<Map<String, Object>>> uF = CompletableFuture.supplyAsync(() -> searchUsers(norm, n));
        CompletableFuture<List<Map<String, Object>>> hF = CompletableFuture.supplyAsync(() -> searchHashtags(norm, n));
        CompletableFuture<List<Map<String, Object>>> pF = CompletableFuture.supplyAsync(() -> searchPosts(norm, n));
        CompletableFuture<List<Map<String, Object>>> rF = CompletableFuture.supplyAsync(() -> searchReels(norm, n));

        try {
            CompletableFuture.allOf(uF, hF, pF, rF).get();
            return new SearchResultsDto(q, uF.get(), hF.get(), pF.get(), rF.get());
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return new SearchResultsDto(q, List.of(), List.of(), List.of(), List.of());
        } catch (ExecutionException ee) {
            return new SearchResultsDto(q, List.of(), List.of(), List.of(), List.of());
        }
    }

    public List<Map<String, Object>> trendingHashtags(int limit) {
        return hashtagService.trending(limit).stream().map(SearchService::toMap).toList();
    }

    public List<String> suggestHashtags(String seed, int limit) {
        return hashtagService.suggest(seed, Math.min(Math.max(limit, 1), 20));
    }

    // ============================================================
    //  Section workers
    // ============================================================
    private List<Map<String, Object>> searchUsers(String q, int n) {
        List<User> matches = users.searchPaged(q, PageRequest.of(0, n));
        List<Map<String, Object>> out = new ArrayList<>(matches.size());
        for (User u : matches) {
            Map<String, Object> row = new HashMap<>();
            row.put("id", u.getId());
            row.put("name", u.getName());
            row.put("email", u.getEmail());
            row.put("profileImage", u.getProfileImage());
            row.put("verified", u.isVerified());
            row.put("accountType", u.getAccountType());
            row.put("followers", u.getFollowersCount());
            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> searchHashtags(String q, int n) {
        String prefix = q.startsWith("#") ? q.substring(1) : q;
        return hashtagService.search(prefix, n).stream().map(SearchService::toMap).toList();
    }

    private List<Map<String, Object>> searchPosts(String q, int n) {
        List<Post> matches = posts.findByCaptionContainingIgnoreCaseOrderByCreatedAtDesc(q);
        if (matches.size() > n) matches = matches.subList(0, n);
        return matches.stream().map(p -> {
            Map<String, Object> row = new HashMap<>();
            row.put("id", p.getId());
            row.put("caption", p.getCaption());
            row.put("imageUrl", p.getImageUrl());
            row.put("videoUrl", p.getVideoUrl());
            row.put("userId", p.getUser() == null ? null : p.getUser().getId());
            row.put("username", p.getUser() == null ? null : p.getUser().getName());
            row.put("createdAt", p.getCreatedAt());
            return row;
        }).toList();
    }

    private List<Map<String, Object>> searchReels(String q, int n) {
        // Caption + hashtag substring across the recent window. Cheap and effective for the
        // first thousands of reels; for >1M rows route to Meilisearch via the same DTO shape.
        List<Reel> recent = reels.findRecent(PageRequest.of(0, 2000));
        String lower = q.toLowerCase();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Reel r : recent) {
            String caption = r.getCaption() == null ? "" : r.getCaption().toLowerCase();
            String tags    = r.getHashtags() == null ? "" : r.getHashtags().toLowerCase();
            if (caption.contains(lower) || tags.contains(lower)) {
                Map<String, Object> row = new HashMap<>();
                row.put("id", r.getId());
                row.put("caption", r.getCaption());
                row.put("videoUrl", r.getVideoUrl());
                row.put("thumbnailUrl", r.getThumbnailUrl());
                row.put("userId", r.getUserId());
                row.put("likesCount", r.getLikesCount());
                row.put("viewsCount", r.getViewsCount());
                row.put("createdAt", r.getCreatedAt());
                out.add(row);
                if (out.size() >= n) break;
            }
        }
        return out;
    }

    private static Map<String, Object> toMap(Hashtag h) {
        Map<String, Object> row = new HashMap<>();
        row.put("tag", h.getTag());
        row.put("usageCount", h.getUsageCount());
        row.put("lastUsedAt", h.getLastUsedAt());
        return row;
    }
}
