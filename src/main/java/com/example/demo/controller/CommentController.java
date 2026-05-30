package com.example.demo.controller;

import com.example.demo.dto.CommentCreateRequest;
import com.example.demo.dto.CommentDto;
import com.example.demo.dto.UserSuggestionDto;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.CommentService;
import com.example.demo.service.CommentSort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST surface for the post-comments ecosystem.
 *
 * <p>Endpoints (all auth-required unless noted):
 *
 * <pre>
 *   GET    /comments/{postId}                  list top-level (sort, page, size)
 *   POST   /comments/{postId}                  create comment (or reply via parentId)
 *   GET    /comments/{commentId}/replies       paginated replies under a comment
 *   PATCH  /comments/{commentId}               edit own comment
 *   DELETE /comments/delete/{commentId}        delete (self / post-owner / admin)
 *
 *   POST   /comments/{commentId}/react?emoji=  switch / clear reaction
 *   POST   /comments/like/{commentId}          legacy heart — alias for emoji=LIKE
 *   DELETE /comments/like/{commentId}          legacy unlike — alias for clear
 *
 *   POST   /comments/{commentId}/pin           pin (post-owner only)
 *   DELETE /comments/{commentId}/pin           unpin
 *
 *   POST   /comments/{commentId}/report        report comment
 *
 *   GET    /comments/mentions/suggest?q=&limit=  mention autocomplete
 * </pre>
 */
@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
@CrossOrigin("*")
public class CommentController {

    private final CommentService commentService;
    private final UserRepository userRepository;

    private User currentUser(Authentication authentication) {
        if (authentication == null) {
            throw new RuntimeException("Not authenticated");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // ---------------- Reads ----------------------------------------------

    @GetMapping("/{postId}")
    public List<CommentDto> list(@PathVariable Long postId,
                                  @RequestParam(defaultValue = "top") String sort,
                                  @RequestParam(defaultValue = "0") int page,
                                  @RequestParam(defaultValue = "30") int size,
                                  Authentication authentication) {
        User viewer = optionalUser(authentication);
        return commentService.list(postId, viewer, CommentSort.of(sort), page, size);
    }

    @GetMapping("/{commentId}/replies")
    public List<CommentDto> replies(@PathVariable Long commentId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "30") int size,
                                      Authentication authentication) {
        User viewer = optionalUser(authentication);
        return commentService.replies(commentId, viewer, page, size);
    }

    // ---------------- Create / edit --------------------------------------

    @PostMapping("/{postId}")
    public CommentDto create(@PathVariable Long postId,
                              @RequestBody CommentCreateRequest body,
                              Authentication authentication) {
        User user = currentUser(authentication);
        return commentService.create(postId, user,
                body.getText(), body.getParentId(), body.getMentionedUserIds());
    }

    @PatchMapping("/{commentId}")
    public CommentDto edit(@PathVariable Long commentId,
                            @RequestBody CommentCreateRequest body,
                            Authentication authentication) {
        User user = currentUser(authentication);
        return commentService.edit(commentId, user,
                body.getText(), body.getMentionedUserIds());
    }

    @DeleteMapping("/delete/{commentId}")
    public ResponseEntity<Map<String, Object>> delete(
            @PathVariable Long commentId, Authentication authentication) {
        User user = currentUser(authentication);
        commentService.delete(commentId, user);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ---------------- Reactions ------------------------------------------

    @PostMapping("/{commentId}/react")
    public ResponseEntity<Map<String, Object>> react(
            @PathVariable Long commentId,
            @RequestParam(required = false) String emoji,
            Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(commentService.react(commentId, user, emoji));
    }

    /** Legacy alias — keeps the old client working. */
    @PostMapping("/like/{commentId}")
    public ResponseEntity<Map<String, Object>> like(
            @PathVariable Long commentId, Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(commentService.react(commentId, user, "LIKE"));
    }

    /** Legacy alias — clears the reaction entirely (matches old semantics). */
    @DeleteMapping("/like/{commentId}")
    public ResponseEntity<Map<String, Object>> unlike(
            @PathVariable Long commentId, Authentication authentication) {
        User user = currentUser(authentication);
        return ResponseEntity.ok(commentService.react(commentId, user, null));
    }

    // ---------------- Pin ------------------------------------------------

    @PostMapping("/{commentId}/pin")
    public CommentDto pin(@PathVariable Long commentId, Authentication authentication) {
        return commentService.pin(commentId, currentUser(authentication));
    }

    @DeleteMapping("/{commentId}/pin")
    public ResponseEntity<Map<String, Object>> unpin(
            @PathVariable Long commentId, Authentication authentication) {
        commentService.unpin(commentId, currentUser(authentication));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    // ---------------- Report ---------------------------------------------

    @PostMapping("/{commentId}/report")
    public ResponseEntity<Map<String, Object>> report(
            @PathVariable Long commentId,
            @RequestBody(required = false) ReportBody body,
            Authentication authentication) {
        User user = currentUser(authentication);
        commentService.report(commentId, user,
                body == null ? null : body.reason,
                body == null ? null : body.note);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    public static class ReportBody {
        public String reason;
        public String note;
    }

    // ---------------- Mention autocomplete -------------------------------

    @GetMapping("/mentions/suggest")
    public List<UserSuggestionDto> suggest(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "8") int limit) {
        if (q == null || q.isBlank()) return List.of();
        var page = PageRequest.of(0, Math.max(1, Math.min(limit, 20)));
        return userRepository.searchPaged(q.trim(), page).stream()
                .map(UserSuggestionDto::from)
                .toList();
    }

    // ---------------------------------------------------------------------

    private User optionalUser(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) return null;
        return userRepository.findByEmail(authentication.getName()).orElse(null);
    }

    // ---------------- Error mapping --------------------------------------
    //
    // Controller-scoped because the project doesn't have a global
    // ControllerAdvice yet; keeps the comments surface from emitting
    // 500s for benign validation failures (moderation blocks, missing
    // entities, permission denials).

    @ExceptionHandler(com.example.demo.service.ContentModerationService.ContentBlockedException.class)
    public ResponseEntity<Map<String, Object>> handleBlocked(
            com.example.demo.service.ContentModerationService.ContentBlockedException e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "moderation",
                "label", e.verdict.label,
                "message", "Your comment was blocked: " + e.verdict.label.toLowerCase()));
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception e) {
        return ResponseEntity.badRequest().body(Map.of(
                "error", "bad_request",
                "message", e.getMessage() == null ? "Invalid request" : e.getMessage()));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleForbidden(
            org.springframework.security.access.AccessDeniedException e) {
        return ResponseEntity.status(403).body(Map.of(
                "error", "forbidden",
                "message", e.getMessage() == null ? "Forbidden" : e.getMessage()));
    }

    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(
            jakarta.persistence.EntityNotFoundException e) {
        return ResponseEntity.status(404).body(Map.of(
                "error", "not_found",
                "message", e.getMessage() == null ? "Not found" : e.getMessage()));
    }
}
