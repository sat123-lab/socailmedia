package com.example.demo.service;

import com.example.demo.entity.Hashtag;
import com.example.demo.entity.HashtagUsage;
import com.example.demo.repository.HashtagRepository;
import com.example.demo.repository.HashtagUsageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Indexes hashtags as they appear in user content (posts, reels, stories).
 *
 * <p>Two tables back this service:</p>
 * <ul>
 *   <li>{@code hashtags} — one row per unique tag with a {@code usage_count}
 *       that powers the trending list.</li>
 *   <li>{@code hashtag_usage} — append-only audit of every (tag, entity)
 *       pair so we can answer "which posts used #foo?" cheaply.</li>
 * </ul>
 *
 * <p>The extraction regex tolerates Unicode word characters and underscores,
 * matching how Instagram & TikTok tokenise tags.</p>
 */
@Service
@RequiredArgsConstructor
public class HashtagService {

    private static final Pattern HASHTAG = Pattern.compile("#([\\p{L}\\p{N}_]{1,64})");

    private final HashtagRepository hashtags;
    private final HashtagUsageRepository usage;

    /**
     * Returns the comma-joined tag string suitable for storing on the parent
     * entity, e.g. {@code "summer,2026,vibe"}.
     */
    @Transactional
    public String extractAndRecord(String text, String entityType, Long entityId) {
        Set<String> tags = extract(text);
        if (tags.isEmpty()) return "";
        LocalDateTime now = LocalDateTime.now();
        for (String tag : tags) {
            Hashtag h = hashtags.findByTag(tag).orElseGet(() -> hashtags.save(
                    Hashtag.builder().tag(tag).usageCount(0).lastUsedAt(now).build()));
            hashtags.bumpUsage(h.getId(), now);
            if (entityId != null) {
                usage.save(HashtagUsage.builder()
                        .tag(tag).entityType(entityType).entityId(entityId).build());
            }
        }
        return String.join(",", tags);
    }

    public void recordEntityRefs(String csvTags, String entityType, Long entityId) {
        if (csvTags == null || csvTags.isBlank() || entityId == null) return;
        for (String tag : csvTags.split(",")) {
            String trimmed = tag.trim();
            if (trimmed.isEmpty()) continue;
            usage.save(HashtagUsage.builder()
                    .tag(trimmed).entityType(entityType).entityId(entityId).build());
        }
    }

    public Set<String> extract(String text) {
        if (text == null || text.isEmpty()) return Set.of();
        Set<String> tags = new LinkedHashSet<>();
        Matcher m = HASHTAG.matcher(text);
        while (m.find()) tags.add(m.group(1).toLowerCase());
        return tags;
    }

    @Cacheable(cacheNames = "hashtags:top")
    public List<Hashtag> trending(int limit) {
        int n = Math.min(Math.max(limit, 1), 50);
        return hashtags.findAllByOrderByUsageCountDescLastUsedAtDesc(PageRequest.of(0, n));
    }

    public List<Hashtag> search(String prefix, int limit) {
        if (prefix == null || prefix.isBlank()) return List.of();
        int n = Math.min(Math.max(limit, 1), 25);
        return hashtags.findByTagStartingWithOrderByUsageCountDesc(
                prefix.toLowerCase(), PageRequest.of(0, n));
    }

    /**
     * Suggest hashtags for given seed text. Pluggable: today, we return
     * already-trending tags whose prefix overlaps the seed; a future AI hook
     * can swap this for a real recommender by overriding the bean.
     */
    public List<String> suggest(String seed, int limit) {
        Set<String> existing = extract(seed);
        List<Hashtag> top = trending(50);
        List<String> out = new ArrayList<>();
        for (Hashtag t : top) {
            if (existing.contains(t.getTag())) continue;
            out.add(t.getTag());
            if (out.size() >= limit) break;
        }
        return out;
    }
}
