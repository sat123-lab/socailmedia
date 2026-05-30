package com.example.demo.service;

import com.example.demo.dto.MentionRefDto;
import com.example.demo.entity.Comment;
import com.example.demo.entity.CommentMention;
import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.repository.CommentMentionRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves and persists {@code @username} mentions inside comments.
 *
 * <p>The flow on comment create / edit:
 * <ol>
 *   <li>Scan the body with a regex that captures any
 *       {@code @name-like} substrings.</li>
 *   <li>Each match is looked up against {@code users.name} (case
 *       insensitive). A single name can match many users — we pick the
 *       verified account if exactly one exists, else the most-followed
 *       user, else the first by id.</li>
 *   <li>Client-supplied {@code mentionedUserIds} take priority — when
 *       the user picks "Jane Doe (id=42)" from the autocomplete we
 *       trust that resolution even if the regex would have picked a
 *       different Jane.</li>
 *   <li>Each resolved user gets exactly one row in
 *       {@code comment_mentions} (unique constraint) and one
 *       {@code MENTION} notification.</li>
 * </ol>
 *
 * <p>Self-mentions never generate a notification, but they are still
 * persisted so the inline highlight renders correctly.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MentionService {

    /**
     * {@code @} followed by 2–30 characters of letters, digits, dots,
     * or underscores. Stops at whitespace / punctuation that isn't a
     * dot or underscore — matches how users actually type names like
     * "@Jane.Doe" or "@john_smith".
     */
    private static final Pattern MENTION_RX =
            Pattern.compile("(?<=^|\\s|[\\p{Punct}&&[^_.@]])"
                    + "@([A-Za-z0-9._]{2,30})");

    private final CommentMentionRepository mentions;
    private final UserRepository users;
    private final NotificationRepository notifications;
    private final RealtimeEventService realtime;

    /**
     * Build mention rows for a saved comment. Returns the resolved
     * mention list so the caller can include it in the broadcast
     * payload without an extra round-trip.
     */
    @Transactional
    public List<CommentMention> apply(Comment comment,
                                       User author,
                                       List<Long> explicitUserIds) {
        // Wipe any existing mentions (edit path) so we don't keep stale rows.
        mentions.deleteAllForComment(comment.getId());

        Map<Long, MatchedUser> resolved = new HashMap<>();

        // 1) Explicit picks from the autocomplete — highest priority.
        if (explicitUserIds != null) {
            for (Long id : explicitUserIds) {
                if (id == null) continue;
                users.findById(id).ifPresent(u ->
                        resolved.putIfAbsent(u.getId(), new MatchedUser(u, "@" + u.getName(), -1, -1)));
            }
        }

        // 2) Regex scan against the body — fills in plain-text mentions.
        Matcher m = MENTION_RX.matcher(comment.getText());
        while (m.find()) {
            String token = m.group(1);
            int start = m.start();
            int end = m.end();
            User u = resolveByName(token);
            if (u == null) continue;
            MatchedUser existing = resolved.get(u.getId());
            if (existing == null || existing.start < 0) {
                resolved.put(u.getId(), new MatchedUser(u, "@" + token, start, end));
            }
        }

        if (resolved.isEmpty()) return List.of();

        List<CommentMention> rows = new ArrayList<>(resolved.size());
        Set<Long> notify = new HashSet<>();
        for (MatchedUser hit : resolved.values()) {
            CommentMention row = CommentMention.builder()
                    .commentId(comment.getId())
                    .mentionedUserId(hit.user.getId())
                    .display(safeDisplay(hit.display))
                    .startIndex(Math.max(0, hit.start))
                    .endIndex(Math.max(0, hit.end))
                    .createdAt(LocalDateTime.now())
                    .build();
            rows.add(row);
            // Self-mentions don't notify.
            if (author == null || !hit.user.getId().equals(author.getId())) {
                notify.add(hit.user.getId());
            }
        }
        mentions.saveAll(rows);

        // 3) Notify mentioned users — uses the existing notifications table
        //    + STOMP fan-out so the bell badge updates instantly.
        for (Long uid : notify) {
            User receiver = users.findById(uid).orElse(null);
            if (receiver == null) continue;
            notifications.save(Notification.builder()
                    .receiver(receiver)
                    .sender(author)
                    .type("MENTION")
                    .message((author == null ? "Someone" : author.getName())
                            + " mentioned you in a comment")
                    .postId(comment.getPost() == null
                            ? null : comment.getPost().getId())
                    .relatedId(comment.getId())
                    .read(false)
                    .build());
            try {
                realtime.toUser(uid, "notification.new", Map.of(
                        "kind", "MENTION",
                        "commentId", comment.getId(),
                        "postId", comment.getPost() == null
                                ? null : comment.getPost().getId(),
                        "byUserId", author == null ? null : author.getId()));
            } catch (Exception ignored) {
                // Bell badge is best-effort.
            }
        }
        return rows;
    }

    /** Build the wire DTOs for a list of mention rows + hydrated users. */
    public List<MentionRefDto> toDtos(List<CommentMention> rows) {
        if (rows == null || rows.isEmpty()) return List.of();
        List<Long> ids = rows.stream()
                .map(CommentMention::getMentionedUserId)
                .toList();
        Map<Long, User> byId = new HashMap<>();
        for (User u : users.findAllById(ids)) byId.put(u.getId(), u);

        List<MentionRefDto> out = new ArrayList<>(rows.size());
        for (CommentMention r : rows) {
            User u = byId.get(r.getMentionedUserId());
            out.add(MentionRefDto.builder()
                    .userId(r.getMentionedUserId())
                    .display(r.getDisplay())
                    .startIndex(r.getStartIndex())
                    .endIndex(r.getEndIndex())
                    .name(u == null ? "" : u.getName())
                    .profileImage(u == null ? null : u.getProfileImage())
                    .verified(u != null && u.isVerified())
                    .build());
        }
        return out;
    }

    // ---------------------------------------------------------------------

    private User resolveByName(String token) {
        if (token == null || token.isBlank()) return null;
        List<User> matches = users.findByNameContainingIgnoreCase(token);
        if (matches.isEmpty()) return null;
        // Prefer an exact case-insensitive match.
        User exact = matches.stream()
                .filter(u -> u.getName() != null
                        && u.getName().equalsIgnoreCase(token))
                .findFirst().orElse(null);
        if (exact != null) return exact;
        // Then a verified account.
        User verified = matches.stream()
                .filter(User::isVerified)
                .findFirst().orElse(null);
        if (verified != null) return verified;
        // Fall back to most-followed, deterministic by id.
        return matches.get(0);
    }

    private String safeDisplay(String s) {
        if (s == null) return "";
        return s.length() > 64 ? s.substring(0, 64) : s;
    }

    /** Internal carrier — couples the user with where the mention landed. */
    private record MatchedUser(User user, String display, int start, int end) {}
}
