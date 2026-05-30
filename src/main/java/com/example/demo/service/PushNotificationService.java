package com.example.demo.service;

import com.example.demo.entity.FcmToken;
import com.example.demo.repository.FcmTokenRepository;
import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scalable FCM push dispatcher.
 *
 * <h3>Design</h3>
 * <ul>
 *   <li>Calls are {@code @Async} — they never block the request thread.</li>
 *   <li>Batched via {@link FirebaseMessaging#sendEachForMulticast} (500/batch).</li>
 *   <li>Tokens that come back {@code UNREGISTERED} / {@code INVALID_ARGUMENT}
 *       are auto-marked invalid so we stop hammering them.</li>
 *   <li>Channel mapping is encoded in the {@code channel} data field; the
 *       Flutter side picks the right notification channel from it.</li>
 *   <li>Up to 3 retries on transient {@code UNAVAILABLE} / {@code INTERNAL}
 *       failures with linear back-off.</li>
 * </ul>
 *
 * <h3>Channels</h3>
 * <table>
 *   <tr><th>Channel</th><th>Used for</th><th>Default sound</th></tr>
 *   <tr><td>messages</td><td>Direct + group chat messages</td><td>chime</td></tr>
 *   <tr><td>calls</td>   <td>Incoming voice / video calls</td><td>ringtone</td></tr>
 *   <tr><td>social</td>  <td>Likes / follows / comments / mentions</td><td>default</td></tr>
 *   <tr><td>stories</td> <td>New story from someone you follow</td><td>default</td></tr>
 *   <tr><td>system</td>  <td>Login alerts, security warnings</td><td>silent</td></tr>
 * </table>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushNotificationService {

    private final FcmTokenRepository tokens;

    /**
     * Convenience for the simplest, most common case — one user, one alert.
     */
    @Async
    public void sendToUser(Long userId, Push push) {
        sendToUsers(List.of(userId), push);
    }

    /**
     * Multi-user fan-out (likes-on-shared-post, group chat messages, etc.).
     */
    @Async
    public void sendToUsers(Collection<Long> userIds, Push push) {
        if (!firebaseAvailable()) {
            log.debug("FCM skipped — Firebase not initialised.");
            return;
        }
        if (userIds == null || userIds.isEmpty()) return;

        List<FcmToken> targets = tokens.findByUserIdInAndInvalidFalse(userIds);
        if (targets.isEmpty()) {
            log.debug("FCM: no live tokens for {} users", userIds.size());
            return;
        }
        // 500-token batch limit per FCM call
        for (int i = 0; i < targets.size(); i += 500) {
            List<FcmToken> chunk = targets.subList(i, Math.min(i + 500, targets.size()));
            dispatch(chunk, push, 0);
        }
    }

    private void dispatch(List<FcmToken> chunk, Push push, int attempt) {
        try {
            MulticastMessage msg = buildMessage(chunk, push);
            BatchResponse resp = FirebaseMessaging.getInstance().sendEachForMulticast(msg);
            handleResponse(chunk, resp);
        } catch (FirebaseMessagingException e) {
            log.warn("FCM batch failed (attempt {}): {}", attempt, e.getMessage());
            if (attempt < 3 && isRetryable(e)) {
                try { Thread.sleep(400L * (attempt + 1)); }
                catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
                dispatch(chunk, push, attempt + 1);
            }
        } catch (Exception e) {
            log.warn("FCM dispatch fatal: {}", e.getMessage());
        }
    }

    private MulticastMessage buildMessage(List<FcmToken> chunk, Push push) {
        List<String> tokenList = new ArrayList<>(chunk.size());
        for (FcmToken t : chunk) tokenList.add(t.getToken());

        Map<String, String> data = new HashMap<>(push.data == null ? Map.of() : push.data);
        data.put("channel", push.channel == null ? "social" : push.channel);
        if (push.deeplink != null) data.put("deeplink", push.deeplink);
        if (push.imageUrl != null) data.put("image", push.imageUrl);

        Notification notif = Notification.builder()
                .setTitle(push.title)
                .setBody(push.body)
                .setImage(push.imageUrl)
                .build();

        return MulticastMessage.builder()
                .addAllTokens(tokenList)
                .setNotification(notif)
                .putAllData(data)
                .setAndroidConfig(AndroidConfig.builder()
                        .setPriority(highPriority(push.channel)
                                ? AndroidConfig.Priority.HIGH : AndroidConfig.Priority.NORMAL)
                        .setCollapseKey(push.collapseKey)
                        .setNotification(AndroidNotification.builder()
                                .setChannelId(push.channel == null ? "social" : push.channel)
                                .setTag(push.collapseKey)
                                .setSound(soundForChannel(push.channel))
                                .build())
                        .build())
                .setApnsConfig(ApnsConfig.builder()
                        .setAps(Aps.builder()
                                .setSound(soundForChannel(push.channel))
                                .setContentAvailable(push.silent)
                                .build())
                        .build())
                .build();
    }

    private void handleResponse(List<FcmToken> chunk, BatchResponse resp) {
        AtomicInteger bad = new AtomicInteger();
        for (int i = 0; i < resp.getResponses().size(); i++) {
            SendResponse r = resp.getResponses().get(i);
            if (r.isSuccessful()) continue;
            String code = r.getException() == null ? "" :
                    String.valueOf(r.getException().getMessagingErrorCode());
            if ("UNREGISTERED".equalsIgnoreCase(code) || "INVALID_ARGUMENT".equalsIgnoreCase(code)) {
                tokens.markInvalid(chunk.get(i).getToken());
                bad.incrementAndGet();
            }
        }
        log.info("FCM batch: success={}, failed={}, invalidated={}",
                resp.getSuccessCount(), resp.getFailureCount(), bad.get());
    }

    private static boolean isRetryable(FirebaseMessagingException e) {
        String code = String.valueOf(e.getMessagingErrorCode());
        return "UNAVAILABLE".equalsIgnoreCase(code) || "INTERNAL".equalsIgnoreCase(code);
    }

    private static boolean highPriority(String channel) {
        return "messages".equalsIgnoreCase(channel) || "calls".equalsIgnoreCase(channel);
    }

    private static String soundForChannel(String c) {
        if ("calls".equalsIgnoreCase(c))   return "ringtone";
        if ("messages".equalsIgnoreCase(c)) return "chime";
        if ("system".equalsIgnoreCase(c))   return null;
        return "default";
    }

    private static boolean firebaseAvailable() {
        return !FirebaseApp.getApps().isEmpty();
    }

    /**
     * Convenience for refreshing the activity timestamp + invalidating
     * stale rows. Cron-ready entry point (not yet scheduled — leave to ops).
     */
    public void markActive(Long userId, String token) {
        tokens.findByToken(token).ifPresent(t -> {
            t.setLastSeenAt(LocalDateTime.now());
            t.setInvalid(false);
            tokens.save(t);
        });
    }

    // ============================================================
    //  Value object
    // ============================================================

    public static class Push {
        public String title;
        public String body;
        public String channel;            // messages | calls | social | stories | system
        public String deeplink;           // myapp://profile/123  or  https://app.foo/p/123
        public String imageUrl;           // rich notification image
        public String collapseKey;        // group notifications (e.g. "chat-42")
        public boolean silent;            // background data-only
        public Map<String, String> data;  // arbitrary payload (string values only — FCM rule)

        public static Push of(String title, String body, String channel) {
            Push p = new Push();
            p.title = title; p.body = body; p.channel = channel;
            return p;
        }

        public Push deeplink(String dl) { this.deeplink = dl; return this; }
        public Push image(String url)   { this.imageUrl = url; return this; }
        public Push collapse(String k)  { this.collapseKey = k; return this; }
        public Push withData(Map<String, String> d) { this.data = d; return this; }
        public Push silent(boolean s)   { this.silent = s; return this; }
    }
}
