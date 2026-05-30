package com.example.demo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Thin façade that pushes events to STOMP topics.
 *
 * <p>Event payloads are plain JSON maps so the Flutter side can dispatch by
 * {@code type} without needing a schema-generated client. Every event carries
 * a {@code type} and a {@code at} ISO-8601 timestamp.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealtimeEventService {

    public static final String TYPE_MESSAGE_DELETED   = "message.deleted";
    public static final String TYPE_MESSAGE_EXPIRED   = "message.expired";
    public static final String TYPE_MESSAGE_READ      = "message.read";
    public static final String TYPE_SESSION_REVOKED   = "session.revoked";
    public static final String TYPE_SECURE_ALERT      = "secure.alert";
    public static final String TYPE_TEMP_BAN          = "secure.tempban";
    /** Fan-out to {@code /topic/admin/private-chats} — redacted metadata only. */
    public static final String TYPE_PRIVATE_CHAT_NEW  = "private.chat.new";

    // ---- V6 — advanced messaging events ---------------------------------
    public static final String TYPE_MESSAGE_REACTION  = "message.reaction";
    public static final String TYPE_MESSAGE_FORWARDED = "message.forwarded";
    public static final String TYPE_MESSAGE_VIEW_ONCE = "message.viewonce";
    public static final String TYPE_TYPING            = "presence.typing";
    public static final String TYPE_PRESENCE          = "presence.update";
    public static final String TYPE_CONV_PINNED       = "chat.pinned";
    public static final String TYPE_CONV_ARCHIVED     = "chat.archived";
    public static final String TYPE_CONV_FOLDERED     = "chat.foldered";

    private final SimpMessagingTemplate template;

    public void toUser(Long userId, String type, Map<String, Object> payload) {
        if (userId == null) return;
        Map<String, Object> envelope = base(type);
        if (payload != null) envelope.putAll(payload);
        send("/topic/user/" + userId, envelope, type);
    }

    public void toAdmins(String type, Map<String, Object> payload) {
        Map<String, Object> envelope = base(type);
        if (payload != null) envelope.putAll(payload);
        send("/topic/admin", envelope, type);
    }

    /**
     * Dedicated channel for the Private-Chats Monitor page so the admin client
     * can subscribe narrowly without filtering every {@code /topic/admin}
     * event. The payload carries only redacted metadata.
     */
    public void toPrivateChatsAdmin(Map<String, Object> payload) {
        Map<String, Object> envelope = base(TYPE_PRIVATE_CHAT_NEW);
        if (payload != null) envelope.putAll(payload);
        send("/topic/admin/private-chats", envelope, TYPE_PRIVATE_CHAT_NEW);
    }

    public void toRoom(String roomId, String type, Map<String, Object> payload) {
        if (roomId == null) return;
        Map<String, Object> envelope = base(type);
        if (payload != null) envelope.putAll(payload);
        send("/topic/room/" + roomId, envelope, type);
    }

    private void send(String destination, Map<String, Object> envelope, String type) {
        try {
            // Disambiguate the overload — cast to Object so we hit
            // convertAndSend(String, Object), not (D, Map<String,Object> headers).
            template.convertAndSend(destination, (Object) envelope);
        } catch (Exception e) {
            log.warn("Failed to push {} to {}: {}", type, destination, e.getMessage());
        }
    }

    private static Map<String, Object> base(String type) {
        Map<String, Object> m = new HashMap<>();
        m.put("type", type);
        m.put("at", Instant.now().toString());
        return m;
    }
}
