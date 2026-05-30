package com.example.demo.dto;

import lombok.Data;

import java.util.List;

/**
 * Inbound shape for {@code POST /comments/{postId}} and
 * {@code PATCH /comments/{commentId}}.
 *
 * <p>{@code mentionedUserIds} lets the client send exact resolutions
 * straight from the mention picker. The service still runs a backup
 * regex-based parse so plain-text mentions also create rows — both
 * sources are deduped on {@code (comment, user)}.
 */
@Data
public class CommentCreateRequest {

    private String text;

    /** Null for top-level comments. */
    private Long parentId;

    /** Optional exact mention resolutions captured at type-time. */
    private List<Long> mentionedUserIds;
}
