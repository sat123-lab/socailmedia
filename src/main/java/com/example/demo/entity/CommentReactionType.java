package com.example.demo.entity;

import java.util.Locale;

/**
 * Canonical list of comment reactions.
 *
 * <p>Persisted as {@code VARCHAR(16)} so the alphabet can grow without
 * a schema change. The 7-way set below matches what the Flutter
 * reaction picker exposes today; new reactions added later just need
 * to add an enum constant and an emoji mapping in the client.
 */
public enum CommentReactionType {

    LIKE("❤️"),
    LAUGH("😂"),
    FIRE("🔥"),
    LOVE("😍"),
    WOW("😮"),
    SAD("😢"),
    CLAP("👏");

    private final String emoji;

    CommentReactionType(String emoji) {
        this.emoji = emoji;
    }

    public String emoji() {
        return emoji;
    }

    /**
     * Strict parser used at the request boundary. Accepts the slug
     * ({@code "LIKE"}) or the raw emoji ({@code "❤️"}); throws on
     * anything else so a typo turns into a 400 instead of being
     * silently coerced to LIKE.
     */
    public static CommentReactionType of(String token) {
        if (token == null) {
            throw new IllegalArgumentException("Reaction is required");
        }
        String t = token.trim();
        if (t.isEmpty()) {
            throw new IllegalArgumentException("Reaction is required");
        }
        for (CommentReactionType r : values()) {
            if (r.name().equalsIgnoreCase(t)) return r;
            if (r.emoji.equals(t)) return r;
        }
        throw new IllegalArgumentException(
                "Unsupported reaction: " + t.toLowerCase(Locale.ROOT));
    }
}
