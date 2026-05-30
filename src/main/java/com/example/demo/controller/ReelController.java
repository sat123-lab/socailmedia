package com.example.demo.controller;

import com.example.demo.dto.CursorPage;
import com.example.demo.dto.ReelDto;
import com.example.demo.entity.ReelComment;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.ReelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Reels REST surface.
 *
 * <ul>
 *   <li>{@code GET /reels?cursor=&limit=&ranking=TRENDING|RECENT}</li>
 *   <li>{@code GET /reels/by-user/{id}?cursor=&limit=}</li>
 *   <li>{@code POST /reels}                                                — create</li>
 *   <li>{@code POST /reels/{id}/like}                                      — toggle like</li>
 *   <li>{@code POST /reels/{id}/share}</li>
 *   <li>{@code POST /reels/{id}/view                                       — telemetry</li>
 *   <li>{@code GET  /reels/{id}/comments?limit=}</li>
 *   <li>{@code POST /reels/{id}/comments}</li>
 *   <li>{@code DELETE /reels/{id}}                                          — soft delete</li>
 * </ul>
 */
@RestController
@RequestMapping("/reels")
@RequiredArgsConstructor
public class ReelController {

    private final ReelService reelService;
    private final UserRepository userRepository;

    @GetMapping
    public CursorPage<ReelDto> feed(@RequestParam(required = false) String cursor,
                                    @RequestParam(defaultValue = "10") int limit,
                                    @RequestParam(defaultValue = "TRENDING") String ranking,
                                    Authentication auth) {
        return reelService.feed(cursor, limit, ranking, currentUserId(auth));
    }

    @GetMapping("/by-user/{userId}")
    public CursorPage<ReelDto> byUser(@PathVariable Long userId,
                                      @RequestParam(required = false) String cursor,
                                      @RequestParam(defaultValue = "12") int limit,
                                      Authentication auth) {
        return reelService.userFeed(userId, cursor, limit, currentUserId(auth));
    }

    @PostMapping
    public ReelDto create(@RequestBody CreateReelRequest req, Authentication auth) {
        Long uid = requireUserId(auth);
        return reelService.create(
                uid, req.caption, req.videoUrl, req.thumbnailUrl,
                req.audioUrl, req.audioTitle,
                req.durationSeconds == null ? 0 : req.durationSeconds,
                req.width, req.height, req.visibility,
                req.overlaysJson);
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<Map<String, Object>> like(@PathVariable Long id, Authentication auth) {
        Long uid = requireUserId(auth);
        boolean liked = reelService.toggleLike(id, uid);
        return ResponseEntity.ok(Map.of("liked", liked));
    }

    @PostMapping("/{id}/share")
    public Map<String, Object> share(@PathVariable Long id) {
        reelService.share(id);
        return Map.of("status", "ok");
    }

    @PostMapping("/{id}/view")
    public Map<String, Object> view(@PathVariable Long id,
                                    @RequestBody(required = false) ViewRequest req,
                                    Authentication auth) {
        int ms = req == null ? 0 : Math.max(0, req.watchMs);
        boolean done = req != null && req.completed;
        reelService.recordView(id, currentUserId(auth), ms, done);
        return Map.of("status", "ok");
    }

    @GetMapping("/{id}/comments")
    public List<ReelComment> comments(@PathVariable Long id,
                                      @RequestParam(defaultValue = "50") int limit) {
        return reelService.listComments(id, limit);
    }

    @PostMapping("/{id}/comments")
    public ReelComment addComment(@PathVariable Long id,
                                  @RequestBody CommentRequest body,
                                  Authentication auth) {
        return reelService.addComment(id, requireUserId(auth), body.text);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable Long id, Authentication auth) {
        Long uid = requireUserId(auth);
        boolean admin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        reelService.delete(id, uid, admin);
        return Map.of("status", "deleted");
    }

    // ------------------------------------------------------------
    private Long requireUserId(Authentication auth) {
        Long id = currentUserId(auth);
        if (id == null) throw new SecurityException("Not authenticated");
        return id;
    }

    private Long currentUserId(Authentication auth) {
        if (auth == null) return null;
        return userRepository.findByEmail(auth.getName()).map(User::getId).orElse(null);
    }

    // ------------------------------------------------------------
    public static class CreateReelRequest {
        public String caption;
        public String videoUrl;
        public String thumbnailUrl;
        public String audioUrl;
        public String audioTitle;
        public Integer durationSeconds;
        public Integer width;
        public Integer height;
        public String visibility;

        /** Opaque editor manifest — see {@link com.example.demo.entity.Reel#overlaysJson}. */
        public String overlaysJson;
    }

    public static class ViewRequest {
        public int watchMs;
        public boolean completed;
    }

    public static class CommentRequest {
        public String text;
    }
}
