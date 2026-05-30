package com.example.demo.security;

import com.example.demo.entity.LoginAttempt;
import com.example.demo.repository.LoginAttemptRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Locks an identity (email / phone) out after too many failed authentication attempts.
 *
 * <p>Window resets when the user succeeds OR when the lockout duration expires —
 * whichever comes first.</p>
 *
 * <p>This protects against vertical brute force (one identity, many attempts). Horizontal
 * brute force (one password, many identities) is handled by the per-IP rate limiter in
 * {@link RateLimitFilter}.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BruteForceProtectionService {

    private final LoginAttemptRepository repository;
    private final SecurityProperties properties;

    /** Should the auth flow refuse this identifier right now? */
    public boolean isLocked(String identifier) {
        if (identifier == null) return false;
        Optional<LoginAttempt> opt = repository.findByIdentifier(identifier.toLowerCase());
        if (opt.isEmpty()) return false;
        LoginAttempt a = opt.get();
        if (a.getLockedUntil() != null && a.getLockedUntil().isAfter(LocalDateTime.now())) {
            return true;
        }
        // expired lock — clear it
        if (a.getLockedUntil() != null) {
            a.setLockedUntil(null);
            a.setFailureCount(0);
            repository.save(a);
        }
        return false;
    }

    public void recordFailure(String identifier) {
        if (identifier == null) return;
        String key = identifier.toLowerCase();
        LoginAttempt a = repository.findByIdentifier(key).orElseGet(() ->
                LoginAttempt.builder()
                        .identifier(key)
                        .failureCount(0)
                        .firstFailureAt(LocalDateTime.now())
                        .build());

        // Window roll: if the first failure was more than 2× lockout-minutes ago, reset.
        Duration window = Duration.ofMinutes(properties.getBruteForce().getLockoutMinutes() * 2L);
        if (a.getFirstFailureAt() != null
                && a.getFirstFailureAt().plus(window).isBefore(LocalDateTime.now())) {
            a.setFailureCount(0);
            a.setFirstFailureAt(LocalDateTime.now());
        }
        a.setFailureCount(a.getFailureCount() + 1);

        if (a.getFailureCount() >= properties.getBruteForce().getMaxAttempts()) {
            a.setLockedUntil(LocalDateTime.now()
                    .plusMinutes(properties.getBruteForce().getLockoutMinutes()));
            log.warn("Identity {} locked until {} ({} failures)",
                    key, a.getLockedUntil(), a.getFailureCount());
        }
        repository.save(a);
    }

    public void recordSuccess(String identifier) {
        if (identifier == null) return;
        repository.findByIdentifier(identifier.toLowerCase()).ifPresent(a -> {
            a.setFailureCount(0);
            a.setLockedUntil(null);
            repository.save(a);
        });
    }
}
