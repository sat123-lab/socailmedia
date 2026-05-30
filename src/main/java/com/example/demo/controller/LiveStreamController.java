package com.example.demo.controller;

import com.example.demo.dto.LiveMessageDto;
import com.example.demo.dto.LiveStreamDto;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.LiveStreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Live-streaming REST surface.
 *
 * <ul>
 *   <li>{@code POST   /live/start}                      — start a stream</li>
 *   <li>{@code POST   /live/{id}/end}                   — end a stream</li>
 *   <li>{@code GET    /live/{id}}                       — stream detail (with rtc token)</li>
 *   <li>{@code GET    /live/trending?category=&limit=}  — discovery list</li>
 *   <li>{@code GET    /live/followed}                   — followed creators currently live</li>
 *   <li>{@code POST   /live/{id}/join}                  — viewer joins (sets presence)</li>
 *   <li>{@code POST   /live/{id}/heartbeat}             — keepalive</li>
 *   <li>{@code POST   /live/{id}/leave}                 — viewer leaves</li>
 *   <li>{@code GET    /live/{id}/messages?limit=}       — chat backfill</li>
 *   <li>{@code POST   /live/{id}/chat}                  — send a chat message</li>
 *   <li>{@code POST   /live/{id}/react}                 — burst a reaction emoji</li>
 *   <li>{@code POST   /live/{id}/pin/{messageId}}       — creator pins</li>
 *   <li>{@code DELETE /live/{id}/message/{messageId}}   — owner or creator deletes</li>
 *   <li>{@code POST   /live/{id}/ban}                   — creator bans / mutes</li>
 *   <li>{@code POST   /live/{id}/unban}                 — creator clears a ban / mute</li>
 *   <li>{@code POST   /live/{id}/slow-mode}             — creator toggles slow mode</li>
 *   <li>{@code POST   /live/{id}/gift}                  — sender sends a gift (architecture)</li>
 * </ul>
 */
@RestController
@RequestMapping("/live")
@RequiredArgsConstructor
public class LiveStreamController {

    private final LiveStreamService liveService;
    private final UserRepository userRepository;

    // ----- lifecycle -------------------------------------------------

    @PostMapping("/start")
    public LiveStreamDto start(@RequestBody LiveStreamService.StartRequest req,
                                Authentication auth) {
        return liveService.start(requireUserId(auth), req);
    }

    @PostMapping("/{id}/end")
    public LiveStreamDto end(@PathVariable Long id, Authentication auth) {
        return liveService.end(id, requireUserId(auth));
    }

    @GetMapping("/{id}")
    public LiveStreamDto detail(@PathVariable Long id, Authentication auth) {
        return liveService.detail(id, currentUserId(auth));
    }

    // ----- discovery --------------------------------------------------

    @GetMapping("/trending")
    public List<LiveStreamDto> trending(@RequestParam(required = false) String category,
                                        @RequestParam(defaultValue = "20") int limit) {
        return liveService.trending(category, limit);
    }

    @GetMapping("/followed")
    public List<LiveStreamDto> followed(Authentication auth) {
        return liveService.followedLive(currentUserId(auth));
    }

    // ----- presence ---------------------------------------------------

    @PostMapping("/{id}/join")
    public LiveStreamDto join(@PathVariable Long id, Authentication auth) {
        return liveService.join(id, requireUserId(auth));
    }

    @PostMapping("/{id}/heartbeat")
    public Map<String, Object> heartbeat(@PathVariable Long id, Authentication auth) {
        liveService.heartbeat(id, requireUserId(auth));
        return Map.of("ok", true);
    }

    @PostMapping("/{id}/leave")
    public Map<String, Object> leave(@PathVariable Long id, Authentication auth) {
        liveService.leave(id, requireUserId(auth));
        return Map.of("ok", true);
    }

    // ----- chat -------------------------------------------------------

    @GetMapping("/{id}/messages")
    public List<LiveMessageDto> messages(@PathVariable Long id,
                                          @RequestParam(defaultValue = "50") int limit) {
        return liveService.recentMessages(id, limit);
    }

    @PostMapping("/{id}/chat")
    public ResponseEntity<?> chat(@PathVariable Long id,
                                  @RequestBody ChatBody body,
                                  Authentication auth) {
        try {
            return ResponseEntity.ok(liveService.sendChat(id, requireUserId(auth), body.body));
        } catch (IllegalStateException slow) {
            return ResponseEntity.status(429)
                    .body(Map.of("error", slow.getMessage()));
        }
    }

    @PostMapping("/{id}/react")
    public Map<String, Object> react(@PathVariable Long id,
                                     @RequestBody ReactBody body,
                                     Authentication auth) {
        liveService.react(id, requireUserId(auth),
                body.emoji, body.count == 0 ? 1 : body.count);
        return Map.of("ok", true);
    }

    @PostMapping("/{id}/pin/{messageId}")
    public Map<String, Object> pin(@PathVariable Long id,
                                   @PathVariable Long messageId,
                                   Authentication auth) {
        liveService.pinMessage(id, messageId, requireUserId(auth));
        return Map.of("ok", true);
    }

    @DeleteMapping("/{id}/message/{messageId}")
    public Map<String, Object> deleteMessage(@PathVariable Long id,
                                             @PathVariable Long messageId,
                                             Authentication auth) {
        liveService.deleteMessage(id, messageId, requireUserId(auth));
        return Map.of("ok", true);
    }

    // ----- moderation -------------------------------------------------

    @PostMapping("/{id}/ban")
    public Map<String, Object> ban(@PathVariable Long id,
                                   @RequestBody BanBody body,
                                   Authentication auth) {
        liveService.ban(id, body.viewerId, requireUserId(auth), body.kind, body.reason);
        return Map.of("ok", true);
    }

    @PostMapping("/{id}/unban")
    public Map<String, Object> unban(@PathVariable Long id,
                                     @RequestBody BanBody body,
                                     Authentication auth) {
        liveService.unban(id, body.viewerId, requireUserId(auth));
        return Map.of("ok", true);
    }

    @PostMapping("/{id}/slow-mode")
    public Map<String, Object> slowMode(@PathVariable Long id,
                                        @RequestBody SlowModeBody body,
                                        Authentication auth) {
        liveService.setSlowMode(id, requireUserId(auth), body.seconds);
        return Map.of("ok", true);
    }

    // ----- gifts ------------------------------------------------------

    @PostMapping("/{id}/gift")
    public Map<String, Object> gift(@PathVariable Long id,
                                    @RequestBody GiftBody body,
                                    Authentication auth) {
        liveService.sendGift(id, requireUserId(auth), body.giftId, body.value);
        return Map.of("ok", true);
    }

    // ------------------------------------------------------------------
    private Long requireUserId(Authentication auth) {
        Long id = currentUserId(auth);
        if (id == null) throw new SecurityException("Not authenticated");
        return id;
    }

    private Long currentUserId(Authentication auth) {
        if (auth == null) return null;
        return userRepository.findByEmail(auth.getName()).map(User::getId).orElse(null);
    }

    // ----- request shapes --------------------------------------------

    public static class ChatBody {
        public String body;
    }
    public static class ReactBody {
        public String emoji;
        public int count;
    }
    public static class BanBody {
        public Long viewerId;
        public String kind;  // BAN | MUTE
        public String reason;
    }
    public static class SlowModeBody {
        public int seconds;
    }
    public static class GiftBody {
        public String giftId;
        public int value;
    }
}
