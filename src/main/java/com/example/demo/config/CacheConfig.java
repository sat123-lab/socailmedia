package com.example.demo.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/**
 * In-process L1 cache for hot read paths.
 *
 * <p>Caches are intentionally short-lived (≤ 60s) so we keep the freshness of
 * the social feed but eliminate repeated DB hits during traffic spikes.
 * Production deployments should layer Redis on top by switching
 * {@code spring.cache.type=redis} — the {@code @Cacheable} annotations on the
 * service layer stay identical.</p>
 *
 * <h3>Buckets</h3>
 * <table>
 *   <tr><th>Name</th><th>TTL</th><th>Max size</th><th>Used by</th></tr>
 *   <tr><td>feed:home</td><td>20s</td><td>10k</td><td>HomeFeedService</td></tr>
 *   <tr><td>feed:reels</td><td>15s</td><td>10k</td><td>ReelService</td></tr>
 *   <tr><td>user:profile</td><td>60s</td><td>50k</td><td>UserService.getProfile</td></tr>
 *   <tr><td>search:trending</td><td>60s</td><td>1k</td><td>SearchService.trending</td></tr>
 *   <tr><td>hashtags:top</td><td>60s</td><td>1k</td><td>SearchService.topHashtags</td></tr>
 * </table>
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager();
        mgr.setAllowNullValues(false);
        mgr.setCacheNames(List.of(
                "feed:home",
                "feed:reels",
                "user:profile",
                "search:trending",
                "hashtags:top",
                "reels:trending"
        ));
        // Default spec; individual caches can override via setCacheSpecification.
        mgr.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofSeconds(30))
                .maximumSize(10_000)
                .recordStats());
        return mgr;
    }
}
