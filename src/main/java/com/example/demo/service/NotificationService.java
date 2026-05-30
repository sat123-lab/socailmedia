package com.example.demo.service;

import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Unified notification entry point. Wrap any place that previously called
 * {@code notificationRepository.save(...)} with one of these helpers so the
 * matching FCM push is sent automatically.
 *
 * <p>The push is fire-and-forget through {@link PushNotificationService}
 * (which itself is {@code @Async}), so callers stay on the critical path.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notifications;
    private final PushNotificationService push;
    private final RealtimeEventService realtime;

    /**
     * Saves the in-app notification, pushes via FCM, and publishes a
     * realtime WebSocket event to the receiver's user topic.
     *
     * @param receiver in-app target
     * @param sender   actor (may be null for system events)
     * @param type     domain-defined: LIKE / COMMENT / FOLLOW / MENTION / …
     * @param body     human-readable description used both in-app and in the push notification
     * @param channel  push channel id: messages | calls | social | stories | system
     * @param deeplink optional deeplink for the push (e.g. {@code app://post/42})
     */
    public Notification deliver(User receiver, User sender, String type, String body,
                                String channel, String deeplink) {
        if (receiver == null || receiver.getId() == null) {
            log.warn("deliver: null receiver, skipping");
            return null;
        }

        Notification entity = Notification.builder()
                .receiver(receiver)
                .sender(sender)
                .type(type)
                .message(body)
                .read(false)
                .build();
        Notification saved = notifications.save(entity);

        // Realtime: in-app badge / list refresh
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", type);
        payload.put("body", body);
        payload.put("notificationId", saved.getId());
        if (sender != null) payload.put("senderId", sender.getId());
        realtime.toUser(receiver.getId(), "notification.new", payload);

        // FCM: deliver as a system notification (and rich data for client to deeplink)
        Map<String, String> data = new HashMap<>();
        data.put("type", type);
        data.put("notificationId", String.valueOf(saved.getId()));
        if (sender != null) data.put("senderId", String.valueOf(sender.getId()));

        PushNotificationService.Push p = PushNotificationService.Push
                .of(senderTitle(sender, type), body, channel == null ? "social" : channel)
                .withData(data)
                .collapse(type + "-" + receiver.getId());
        if (deeplink != null) p.deeplink(deeplink);

        try {
            push.sendToUser(receiver.getId(), p);
        } catch (Exception e) {
            log.warn("FCM push failed (non-fatal): {}", e.getMessage());
        }

        return saved;
    }

    private static String senderTitle(User sender, String type) {
        if (sender != null && sender.getName() != null) return sender.getName();
        if ("SYSTEM".equalsIgnoreCase(type)) return "System";
        return "Someone";
    }
}
