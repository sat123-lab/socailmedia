package com.example.demo.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of nonces seen on signed requests.
 *
 * <p>Used by {@link RequestSignatureFilter} to reject replays — every
 * signed request must carry a unique nonce, and we keep the seen ones
 * around for {@code window} seconds (defaults to the signature TTL).</p>
 *
 * <p>For multi-instance deployments swap the {@link ConcurrentHashMap} for
 * a Redis SET or DynamoDB TTL-table.</p>
 */
@Service
@Slf4j
public class NonceCacheService {

    private static final long WINDOW_MS = 5 * 60 * 1000L; // 5 minutes
    private final ConcurrentHashMap<String, Long> seen = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    /** Returns {@code true} if this is the first time the nonce was offered. */
    public boolean register(String nonce) {
        if (nonce == null || nonce.isBlank()) return false;
        long now = System.currentTimeMillis();
        Long prev = seen.putIfAbsent(nonce, now);
        return prev == null;
    }

    /** Generates a fresh 24-byte nonce (server-side helpers). */
    public String mintNonce() {
        byte[] b = new byte[24];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Drops nonces older than the window. Hourly is plenty. */
    @Scheduled(fixedDelay = 60_000L)
    void evictOld() {
        long cutoff = System.currentTimeMillis() - WINDOW_MS;
        Iterator<Map.Entry<String, Long>> it = seen.entrySet().iterator();
        int removed = 0;
        while (it.hasNext()) {
            if (it.next().getValue() < cutoff) {
                it.remove();
                removed++;
            }
        }
        if (removed > 0 && log.isDebugEnabled()) {
            log.debug("NonceCache evicted {} entries (size={}).", removed, seen.size());
        }
    }
}
