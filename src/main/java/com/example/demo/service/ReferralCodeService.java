package com.example.demo.service;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

/**
 * Generates and resolves user-facing referral codes.
 *
 * <p>Codes are 8-character strings drawn from an alphabet of 31
 * symbols (no 0/1/I/O — picking-from-paper safe). That's
 * {@code 31^8 ≈ 8.5e11} possibilities — enough to cover billions of
 * users without collisions becoming meaningful at our scale.
 *
 * <p>Generation strategy:
 * <ol>
 *   <li>Roll a candidate via {@link SecureRandom}.</li>
 *   <li>Re-roll up to 5× if {@link UserRepository#existsByReferralCode}
 *       returns true (defence against the birthday paradox at scale).</li>
 *   <li>Persist the code via a tight transaction so concurrent mints
 *       can't race onto the same value.</li>
 * </ol>
 *
 * <p>Codes are immutable once minted. Users can rotate by an explicit
 * admin tool — not exposed here.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ReferralCodeService {

    private static final char[] ALPHABET =
            "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final int CODE_LENGTH = 8;
    private static final int MAX_ATTEMPTS = 5;
    private static final SecureRandom RNG = new SecureRandom();

    private final UserRepository users;

    /**
     * Return the user's referral code, minting one on first call.
     * Idempotent — repeated calls return the existing code.
     */
    @Transactional
    public String ensureCode(User user) {
        if (user.getReferralCode() != null && !user.getReferralCode().isEmpty()) {
            return user.getReferralCode();
        }
        String fresh = generateUniqueCode();
        user.setReferralCode(fresh);
        users.save(user);
        log.info("Minted referral code {} for user {}", fresh, user.getId());
        return fresh;
    }

    /** Strict lookup: returns null when the code doesn't resolve. */
    public User resolve(String code) {
        if (code == null || code.isBlank()) return null;
        return users.findByReferralCodeIgnoreCase(normalise(code)).orElse(null);
    }

    /**
     * Normalise codes everywhere they enter the system — trim, upper,
     * strip the optional leading '#'. Web links sometimes carry
     * `?ref=#ABCD1234` after a hash-router intermediate.
     */
    public static String normalise(String code) {
        if (code == null) return null;
        String n = code.trim().toUpperCase();
        if (n.startsWith("#")) n = n.substring(1);
        return n;
    }

    // ------------------------------------------------------------------

    private String generateUniqueCode() {
        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            String candidate = roll();
            if (!users.existsByReferralCode(candidate)) {
                return candidate;
            }
        }
        // After MAX_ATTEMPTS the chance of a real collision is
        // astronomical — almost certainly contention noise. Fall through
        // and let the unique-index constraint reject if it really lost.
        return roll();
    }

    private String roll() {
        char[] buf = new char[CODE_LENGTH];
        for (int i = 0; i < CODE_LENGTH; i++) {
            buf[i] = ALPHABET[RNG.nextInt(ALPHABET.length)];
        }
        return new String(buf);
    }
}
