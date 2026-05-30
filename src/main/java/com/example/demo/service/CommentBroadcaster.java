package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * STOMP fan-out for the comments ecosystem.
 *
 * <p>Topic: {@code /topic/post/{postId}/comments}. Every subscriber on
 * that topic receives every event for the post — the Flutter client
 * dispatches by {@code type} so we don't need a topic per event kind.
 *
 * <p>Event types:
 * <ul>
 *   <li>{@code comment.new}      — payload: {@code dto}</li>
 *   <li>{@code comment.edited}   — payload: {@code dto}</li>
 *   <li>{@code comment.deleted}  — payload: {@code commentId}</li>
 *   <li>{@code comment.reaction} — payload: {@code commentId, reactions, total}</li>
 *   <li>{@code comment.pinned}   — payload: {@code commentId} (null = unpinned)</li>
 * </ul>
 *
 * <p>Events are published *after* the database transaction commits so
 * a subscriber that immediately calls back into the API never sees a
 * stale read. The {@code @TransactionalEventListener} machinery lives
 * in {@code CommentService} — this class is only the wire.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CommentBroadcaster {

    public static final String TYPE_NEW       = "comment.new";
    public static final String TYPE_EDITED    = "comment.edited";
    public static final String TYPE_DELETED   = "comment.deleted";
    public static final String TYPE_REACTION  = "comment.reaction";
    public static final String TYPE_PINNED    = "comment.pinned";

    private final SimpMessagingTemplate template;

    public void broadcast(Long postId, String type, Map<String, Object> payload) {
        if (postId == null) return;
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("type", type);
        envelope.put("at", Instant.now().toString());
        envelope.put("postId", postId);
        if (payload != null) envelope.putAll(payload);
        try {
            template.convertAndSend("/topic/post/" + postId + "/comments",
                    (Object) envelope);
        } catch (Exception e) {
            log.warn("comment broadcast {} failed for post {}: {}",
                    type, postId, e.getMessage());
        }
    }
}
