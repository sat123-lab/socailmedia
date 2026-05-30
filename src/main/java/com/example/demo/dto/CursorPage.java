package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Generic cursor-paginated response envelope. Used by the high-traffic feeds
 * (home feed, reels, search) where offset-based pagination breaks down at
 * scale (deep pages get expensive, and inserts shift the offsets).
 *
 * <p>The cursor is opaque to the client — typically the {@code createdAt}
 * timestamp + {@code id} of the last row, base64-encoded by the service.</p>
 *
 * <pre>
 * GET /feed/home?cursor=eyJ0IjoxNzE5...&amp;limit=20
 *   → { items: [...], nextCursor: "..." | null, hasMore: true }
 * </pre>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class CursorPage<T> {
    private List<T> items;
    private String nextCursor;
    private boolean hasMore;

    public static <T> CursorPage<T> of(List<T> items, String next) {
        return new CursorPage<>(items, next, next != null);
    }
}
