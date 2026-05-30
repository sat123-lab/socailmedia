package com.example.demo.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Small, dependency-free SHA helpers — extracted so the referral
 * fraud table can hash IPs and device IDs consistently with the rest
 * of the codebase. Several services previously had private one-off
 * copies; new callers should use this class.
 */
public final class HashUtil {

    private HashUtil() {}

    /** SHA-256, returns lower-case hex (64 chars). */
    public static String sha256Hex(String input) {
        if (input == null || input.isEmpty()) return "";
        return sha256Hex(input.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input));
        } catch (NoSuchAlgorithmException e) {
            // Every JVM ships SHA-256; never thrown in practice.
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /**
     * Truncated SHA-256 — handy for keys that need to be short but still
     * collision-resistant for our scale (~1 in 4 billion at 16 hex chars).
     */
    public static String sha256Short(String input, int hexChars) {
        String full = sha256Hex(input);
        if (hexChars >= full.length()) return full;
        return full.substring(0, Math.max(8, hexChars));
    }
}
