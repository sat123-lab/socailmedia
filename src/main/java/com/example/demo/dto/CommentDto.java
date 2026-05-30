package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Read-side representation of a comment. Kept independent of the
 * {@code Comment} entity so we can evolve the wire format (e.g. adding
 * translated text, edit history, moderation badges) without leaking
 * persistence concerns.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentDto {

    private Long id;
    private Long postId;
    private Long parentId;
    private int depth;

    /** Plain body text. When {@link #deleted}, this is {@code "[deleted]"}. */
    private String text;

    private LocalDateTime createdAt;
    private LocalDateTime editedAt;

    private boolean deleted;
    private boolean pinned;
    private LocalDateTime pinnedAt;

    private int replyCount;

    // ---- author chrome -----------------------------------------------------
    /** Null when {@link #deleted} is true. */
    private Long authorId;
    private String authorName;
    private String authorProfileImage;
    private boolean authorVerified;
    /** {@code PERSONAL | CREATOR | BUSINESS}; null if missing. */
    private String authorAccountType;

    // ---- reactions ---------------------------------------------------------
    /** Emoji slug → count. Empty when the comment has no reactions. */
    private Map<String, Long> reactions;

    /** Total reactions across all emojis (cheap convenience for the UI). */
    private long reactionsTotal;

    /** Backwards-compatible heart count — populated from the LIKE reaction bucket. */
    private long likesCount;

    /** Slug of the viewer's current reaction, or {@code null}. */
    private String myReaction;

    /** True when {@link #myReaction} is {@code LIKE} — keeps the old client happy. */
    private boolean likedByMe;

    // ---- mentions ----------------------------------------------------------
    private List<MentionRefDto> mentions;

    /** Did the post owner author this comment? Shown as a "Creator" pill. */
    private boolean byPostOwner;
}
