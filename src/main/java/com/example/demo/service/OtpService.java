package com.example.demo.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory OTP store for phone verification.
 *
 * Production: replace {@link #deliver} with a real SMS provider
 * (Twilio, MSG91, AWS SNS etc.). For development the code is logged
 * to the backend console so the same code can be entered in the app.
 */
@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private static final Duration TTL = Duration.ofMinutes(5);
    private static final int MAX_ATTEMPTS = 5;

    private final SecureRandom random = new SecureRandom();
    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    public String issueOtp(String phone) {
        String normalised = normalise(phone);
        String code = String.format("%06d", random.nextInt(1_000_000));
        store.put(normalised, new Entry(code, Instant.now().plus(TTL), 0));
        deliver(normalised, code);
        return code;
    }

    public boolean verify(String phone, String otp) {
        String normalised = normalise(phone);
        Entry entry = store.get(normalised);
        if (entry == null) return false;

        if (Instant.now().isAfter(entry.expiresAt)) {
            store.remove(normalised);
            return false;
        }

        entry.attempts++;
        if (entry.attempts > MAX_ATTEMPTS) {
            store.remove(normalised);
            return false;
        }

        boolean ok = entry.code.equals(otp == null ? "" : otp.trim());
        if (ok) {
            store.remove(normalised);
        }
        return ok;
    }

    private void deliver(String phone, String code) {
        log.info("[OTP] {} -> {} (valid {} minutes)", phone, code, TTL.toMinutes());
    }

    private String normalise(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[^0-9+]", "");
    }

    private static final class Entry {
        final String code;
        final Instant expiresAt;
        int attempts;

        Entry(String code, Instant expiresAt, int attempts) {
            this.code = code;
            this.expiresAt = expiresAt;
            this.attempts = attempts;
        }
    }
}
