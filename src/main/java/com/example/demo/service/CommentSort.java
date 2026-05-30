package com.example.demo.service;

/**
 * Server-side sort options for the comments list endpoint.
 *
 * <ul>
 *   <li>{@link #TOP} — pinned first, then by hot score, then newest.</li>
 *   <li>{@link #MOST_LIKED} — same as TOP but ignores recency, breaks
 *       ties on createdAt DESC.</li>
 *   <li>{@link #NEWEST} — pinned first, then strict createdAt DESC.</li>
 *   <li>{@link #OLDEST} — strict createdAt ASC (pinned still float).</li>
 * </ul>
 */
public enum CommentSort {
    TOP, MOST_LIKED, NEWEST, OLDEST;

    /**
     * Lenient parser — defaults to {@link #TOP} on null / unknown so a
     * client that sends garbage just falls back to the recommended
     * default instead of getting a 400.
     */
    public static CommentSort of(String token) {
        if (token == null) return TOP;
        String t = token.trim().toUpperCase().replace('-', '_');
        for (CommentSort s : values()) {
            if (s.name().equals(t)) return s;
        }
        return TOP;
    }
}
