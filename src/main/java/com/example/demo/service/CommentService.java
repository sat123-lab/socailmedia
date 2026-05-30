package com.example.demo.service;

import com.example.demo.dto.CommentDto;
import com.example.demo.dto.MentionRefDto;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Heart of the comments ecosystem.
 *
 * <p>Centralises every read and write path so the controller stays
 * thin and so every mutation has a predictable side-effect set
 * (counter maintenance, mention resolution, moderation screening,
 * STOMP fan-out, notifications).
 *
 * <p>Atomicity model:
 * <ul>
 *   <li>All write methods are {@code @Transactional} — failures roll
 *       back together so we never end up with an orphan
 *       {@code comment_reactions} row or a mismatched
 *       {@code Post.commentsCount}.</li>
 *   <li>STOMP broadcasts and FCM pushes happen *inside* the
 *       transaction. The fan-out itself is wrapped in try/catch so a
 *       broker failure can't roll back the user's comment.</li>
 *   <li>The legacy {@code comment_likes} table is kept in sync with
 *       {@code LIKE} reactions — old endpoints / clients keep working
 *       without a flag day.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository comments;
    private final CommentReactionRepository reactions;
    private final CommentLikeRepository legacyLikes;
    private final CommentMentionRepository mentions;
    private final CommentReportRepository reports;
    private final UserRepository users;
    private final PostRepository posts;
    private final NotificationRepository notifications;
    private final ContentModerationService moderation;
    private final MentionService mentionService;
    private final CommentBroadcaster broadcaster;
    private final RealtimeEventService realtime;

    // =====================================================================
    //  PHASE 1 — Threaded fetch
    // =====================================================================

    /**
     * Top-level comments for a post, sorted per the requested mode.
     * Replies are returned separately via {@link #replies}.
     */
    @Transactional(readOnly = true)
    public List<CommentDto> list(Long postId, User viewer, CommentSort sort,
                                  int page, int size) {
        Post post = posts.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found"));
        var pageable = PageRequest.of(Math.max(0, page),
                Math.max(1, Math.min(size, 100)));
        List<Comment> rows;
        switch (sort) {
            case NEWEST -> rows = comments.findTopLevelByNewest(post.getId(), pageable);
            case OLDEST -> rows = comments.findTopLevel(post.getId(), pageable);
            case MOST_LIKED, TOP -> rows = comments.findTopLevelByTop(post.getId(), pageable);
            default -> rows = comments.findTopLevelByTop(post.getId(), pageable);
        }
        return hydrate(rows, post, viewer);
    }

    /**
     * Replies under one parent comment. Cursor is the smallest reply
     * id you've already loaded — pass {@code null} for the first page.
     */
    @Transactional(readOnly = true)
    public List<CommentDto> replies(Long parentId, User viewer,
                                     int page, int size) {
        Comment parent = comments.findById(parentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));
        var pageable = PageRequest.of(Math.max(0, page),
                Math.max(1, Math.min(size, 100)));
        List<Comment> rows = comments.findReplies(parent.getId(), pageable);
        return hydrate(rows, parent.getPost(), viewer);
    }

    // =====================================================================
    //  PHASE 1 + 3 + 10 — Create
    // =====================================================================

    @Transactional
    public CommentDto create(Long postId, User author, String rawText,
                              Long parentId, List<Long> mentionedUserIds) {
        Post post = posts.findById(postId)
                .orElseThrow(() -> new EntityNotFoundException("Post not found"));
        String text = sanitise(rawText);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Comment text is required");
        }
        // PHASE 10 — moderation hook. Throws ContentBlockedException on bad input;
        // the global exception handler maps that to 400.
        moderation.assertAllowed(text);

        int depth = 0;
        Comment parent = null;
        if (parentId != null) {
            parent = comments.findById(parentId)
                    .orElseThrow(() -> new EntityNotFoundException("Parent comment not found"));
            if (!parent.getPost().getId().equals(post.getId())) {
                throw new IllegalArgumentException("Parent comment is on a different post");
            }
            if (parent.isDeleted()) {
                throw new IllegalArgumentException("Cannot reply to a deleted comment");
            }
            depth = parent.getDepth() + 1;
        }

        Comment row = Comment.builder()
                .text(text)
                .createdAt(LocalDateTime.now())
                .user(author)
                .post(post)
                .parentId(parentId)
                .depth(depth)
                .pinned(false)
                .deleted(false)
                .build();
        row = comments.save(row);

        if (parent != null) {
            comments.incrementReplyCount(parent.getId());
        }

        // PHASE 3 — resolve + persist mentions, fire notifications.
        List<CommentMention> mentionRows = mentionService.apply(row, author, mentionedUserIds);

        // post.commentsCount counts everything (top-level + replies) for
        // backward compatibility with existing feed badges.
        post.setCommentsCount((int) comments.countByPost(post));
        posts.save(post);

        // Notify the post owner — only for *top-level* comments, to match
        // the existing product behaviour (replies notify the parent author
        // instead).
        if (parent == null && !post.getUser().getId().equals(author.getId())) {
            saveNotification(post.getUser(), author, "COMMENT",
                    author.getName() + " commented on your post",
                    post.getId(), row.getId());
        } else if (parent != null
                && parent.getUser() != null
                && !parent.getUser().getId().equals(author.getId())) {
            saveNotification(parent.getUser(), author, "COMMENT_REPLY",
                    author.getName() + " replied to your comment",
                    post.getId(), row.getId());
        }

        CommentDto dto = toDto(row, viewerOf(author), mentionRows,
                /*reactionMap*/ Map.of(), /*myReaction*/ null,
                isPostOwner(row, post));
        // PHASE 7 — push to subscribers.
        broadcaster.broadcast(post.getId(), CommentBroadcaster.TYPE_NEW,
                Map.of("comment", dto));
        return dto;
    }

    // =====================================================================
    //  PHASE 4 — Edit
    // =====================================================================

    @Transactional
    public CommentDto edit(Long commentId, User author, String rawText,
                            List<Long> mentionedUserIds) {
        Comment row = comments.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));
        if (!row.getUser().getId().equals(author.getId())) {
            throw new AccessDeniedException("You can only edit your own comments");
        }
        if (row.isDeleted()) {
            throw new IllegalStateException("Deleted comments cannot be edited");
        }
        String text = sanitise(rawText);
        if (text.isEmpty()) {
            throw new IllegalArgumentException("Comment text is required");
        }
        moderation.assertAllowed(text);
        row.setText(text);
        row.setEditedAt(LocalDateTime.now());
        row = comments.save(row);

        List<CommentMention> mentionRows =
                mentionService.apply(row, author, mentionedUserIds);

        CommentDto dto = toDto(row, author, mentionRows,
                aggregateReactions(row.getId()), myReactionFor(row.getId(), author),
                isPostOwner(row, row.getPost()));
        broadcaster.broadcast(row.getPost().getId(),
                CommentBroadcaster.TYPE_EDITED, Map.of("comment", dto));
        return dto;
    }

    // =====================================================================
    //  PHASE 4 — Delete (soft for top-level w/ replies; hard otherwise)
    // =====================================================================

    @Transactional
    public void delete(Long commentId, User actor) {
        Comment row = comments.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));
        boolean isAuthor = row.getUser().getId().equals(actor.getId());
        boolean isPostOwner = row.getPost().getUser().getId().equals(actor.getId());
        boolean isAdmin = actor.isAdmin();
        if (!isAuthor && !isPostOwner && !isAdmin) {
            throw new AccessDeniedException("You cannot delete this comment");
        }

        Post post = row.getPost();
        int removed;
        if (row.getReplyCount() > 0) {
            // Soft delete — preserves the thread shape so existing replies
            // don't dangle. The DTO redacts the body + author chrome.
            row.setDeleted(true);
            row.setText("[deleted]");
            comments.save(row);
            // Reactions on a deleted comment are dropped — they can't be
            // re-added by the UI and they keep the hot-score honest.
            reactions.deleteAllForComment(row.getId());
            legacyLikes.deleteByComment(row);
            mentions.deleteAllForComment(row.getId());
            removed = 1;
        } else {
            // Hard delete — no replies anchored to this row.
            removed = hardDelete(row);
        }

        if (row.getParentId() != null) {
            comments.decrementReplyCount(row.getParentId());
        }

        post.setCommentsCount(Math.max(0, post.getCommentsCount() - removed));
        posts.save(post);

        broadcaster.broadcast(post.getId(), CommentBroadcaster.TYPE_DELETED,
                Map.of("commentId", row.getId(),
                        "parentId", row.getParentId() == null ? -1 : row.getParentId(),
                        "soft", row.isDeleted()));
    }

    private int hardDelete(Comment row) {
        reactions.deleteAllForComment(row.getId());
        legacyLikes.deleteByComment(row);
        mentions.deleteAllForComment(row.getId());
        comments.delete(row);
        return 1;
    }

    // =====================================================================
    //  PHASE 2 — React
    // =====================================================================

    /**
     * Add or switch reaction. Pass {@code null}/empty emoji to clear
     * the viewer's reaction. Returns the new aggregate map so the
     * client can render without a follow-up GET.
     */
    @Transactional
    public Map<String, Object> react(Long commentId, User viewer, String emojiToken) {
        Comment row = comments.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));
        if (row.isDeleted()) {
            throw new IllegalStateException("Cannot react to a deleted comment");
        }
        boolean clearing = emojiToken == null || emojiToken.isBlank();
        CommentReactionType emoji = clearing ? null : CommentReactionType.of(emojiToken);

        var existing = reactions.findByCommentIdAndUserId(commentId, viewer.getId())
                .orElse(null);
        if (clearing) {
            if (existing != null) reactions.delete(existing);
        } else if (existing == null) {
            reactions.save(CommentReaction.builder()
                    .commentId(commentId)
                    .userId(viewer.getId())
                    .emoji(emoji)
                    .createdAt(LocalDateTime.now())
                    .build());
        } else if (existing.getEmoji() != emoji) {
            existing.setEmoji(emoji);
            reactions.save(existing);
        }

        // Mirror LIKE state into legacy table so /comments/like/{id} keeps working.
        boolean isLike = emoji == CommentReactionType.LIKE;
        boolean hadLike = legacyLikes.existsByCommentAndUser(row, viewer);
        if (isLike && !hadLike) {
            legacyLikes.save(CommentLike.builder()
                    .comment(row).user(viewer).build());
        } else if (!isLike && hadLike) {
            legacyLikes.deleteByCommentAndUser(row, viewer);
        }

        Map<String, Long> counts = aggregateReactions(commentId);
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        // PHASE 6 — keep hot_score fresh so "Top" sort reflects engagement.
        double hot = total + (counts.getOrDefault("LIKE", 0L) * 0.5);
        comments.updateHotScore(commentId, hot);

        String mine = emoji == null ? null : emoji.name();
        long likeCount = counts.getOrDefault("LIKE", 0L);

        Map<String, Object> payload = new HashMap<>();
        payload.put("commentId", commentId);
        payload.put("reactions", counts);
        payload.put("reactionsTotal", total);
        payload.put("likesCount", likeCount);
        payload.put("myReaction", mine);
        payload.put("likedByMe", mine != null
                && mine.equals(CommentReactionType.LIKE.name()));
        broadcaster.broadcast(row.getPost().getId(),
                CommentBroadcaster.TYPE_REACTION, payload);

        // Notify the comment author (first-time reaction only) — keeps the
        // notification table small without making the bell feel dead.
        if (!clearing
                && existing == null
                && row.getUser() != null
                && !row.getUser().getId().equals(viewer.getId())) {
            saveNotification(row.getUser(), viewer, "COMMENT_REACTION",
                    viewer.getName() + " " + emoji.emoji()
                            + " reacted to your comment",
                    row.getPost().getId(), commentId);
        }
        return payload;
    }

    // =====================================================================
    //  PHASE 5 — Pin / unpin (post owner only)
    // =====================================================================

    @Transactional
    public CommentDto pin(Long commentId, User actor) {
        Comment row = comments.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));
        Post post = row.getPost();
        if (!post.getUser().getId().equals(actor.getId()) && !actor.isAdmin()) {
            throw new AccessDeniedException("Only the post owner can pin comments");
        }
        if (row.isDeleted()) {
            throw new IllegalStateException("Cannot pin a deleted comment");
        }
        // Enforce "one pinned per post" atomically.
        comments.clearPin(post.getId());
        row.setPinned(true);
        row.setPinnedAt(LocalDateTime.now());
        row.setPinnedByUserId(actor.getId());
        row = comments.save(row);

        CommentDto dto = toDto(row, actor,
                mentions.findByCommentId(row.getId()),
                aggregateReactions(row.getId()),
                myReactionFor(row.getId(), actor),
                isPostOwner(row, post));
        broadcaster.broadcast(post.getId(), CommentBroadcaster.TYPE_PINNED,
                Map.of("commentId", row.getId(), "pinned", true,
                        "comment", dto));
        return dto;
    }

    @Transactional
    public void unpin(Long commentId, User actor) {
        Comment row = comments.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));
        Post post = row.getPost();
        if (!post.getUser().getId().equals(actor.getId()) && !actor.isAdmin()) {
            throw new AccessDeniedException("Only the post owner can unpin comments");
        }
        if (!row.isPinned()) return;
        row.setPinned(false);
        row.setPinnedAt(null);
        row.setPinnedByUserId(null);
        comments.save(row);
        broadcaster.broadcast(post.getId(), CommentBroadcaster.TYPE_PINNED,
                Map.of("commentId", row.getId(), "pinned", false));
    }

    // =====================================================================
    //  PHASE 4 — Report
    // =====================================================================

    @Transactional
    public void report(Long commentId, User reporter, String reason, String note) {
        Comment row = comments.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found"));
        String r = (reason == null || reason.isBlank())
                ? "other" : reason.trim().toLowerCase();
        if (r.length() > 32) r = r.substring(0, 32);
        var existing = reports.findByCommentIdAndReporterUserId(
                row.getId(), reporter.getId());
        if (existing.isPresent()) return;
        reports.save(CommentReport.builder()
                .commentId(row.getId())
                .reporterUserId(reporter.getId())
                .reason(r)
                .note(safeTrim(note, 500))
                .status(CommentReport.Status.OPEN)
                .createdAt(LocalDateTime.now())
                .build());
        // Surface to admins via the existing realtime channel so the
        // moderation queue light up immediately.
        try {
            realtime.toAdmins("comment.reported", Map.of(
                    "commentId", row.getId(),
                    "postId", row.getPost().getId(),
                    "reason", r,
                    "reporterId", reporter.getId()));
        } catch (Exception ignored) {
            // Admin lights are best-effort.
        }
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    private void saveNotification(User receiver, User sender, String type,
                                   String message, Long postId, Long relatedId) {
        notifications.save(Notification.builder()
                .receiver(receiver)
                .sender(sender)
                .type(type)
                .message(message)
                .postId(postId)
                .relatedId(relatedId)
                .read(false)
                .build());
        // Light up the bell badge in realtime — only on the viewer who
        // received the event (privacy + bandwidth).
        try {
            realtime.toUser(receiver.getId(), "notification.new", Map.of(
                    "kind", type,
                    "postId", postId,
                    "commentId", relatedId == null ? null : relatedId,
                    "byUserId", sender == null ? null : sender.getId()));
        } catch (Exception ignored) {}
    }

    /** Hydrate a list of comments with reactions / mentions / viewer state. */
    private List<CommentDto> hydrate(List<Comment> rows, Post post, User viewer) {
        if (rows == null || rows.isEmpty()) return List.of();
        List<Long> ids = rows.stream().map(Comment::getId).toList();

        // Reaction aggregates — one query for the whole page.
        Map<Long, Map<String, Long>> counts = new HashMap<>();
        for (Object[] r : reactions.countsForMany(ids)) {
            Long cid = (Long) r[0];
            CommentReactionType t = (CommentReactionType) r[1];
            long n = ((Number) r[2]).longValue();
            counts.computeIfAbsent(cid, k -> new HashMap<>()).put(t.name(), n);
        }
        // Viewer's own reactions.
        Map<Long, String> mine = new HashMap<>();
        if (viewer != null) {
            for (Object[] r : reactions.myReactions(viewer.getId(), ids)) {
                mine.put((Long) r[0], ((CommentReactionType) r[1]).name());
            }
        }
        // Mentions — single query, grouped client-side.
        Map<Long, List<CommentMention>> mentionMap = new HashMap<>();
        for (CommentMention m : mentions.findForMany(ids)) {
            mentionMap.computeIfAbsent(m.getCommentId(), k -> new ArrayList<>())
                    .add(m);
        }

        List<CommentDto> out = new ArrayList<>(rows.size());
        for (Comment c : rows) {
            out.add(toDto(c, viewer,
                    mentionMap.getOrDefault(c.getId(), List.of()),
                    counts.getOrDefault(c.getId(), Map.of()),
                    mine.get(c.getId()),
                    isPostOwner(c, post)));
        }
        return out;
    }

    private CommentDto toDto(Comment c,
                              User viewer,
                              List<CommentMention> mentionRows,
                              Map<String, Long> reactionCounts,
                              String myReaction,
                              boolean byPostOwner) {
        long total = reactionCounts.values().stream().mapToLong(Long::longValue).sum();
        long likeCount = reactionCounts.getOrDefault("LIKE", 0L);
        List<MentionRefDto> mentionDtos = mentionService.toDtos(mentionRows);

        return CommentDto.builder()
                .id(c.getId())
                .postId(c.getPost() == null ? null : c.getPost().getId())
                .parentId(c.getParentId())
                .depth(c.getDepth())
                .text(c.isDeleted() ? "[deleted]" : c.getText())
                .createdAt(c.getCreatedAt())
                .editedAt(c.getEditedAt())
                .deleted(c.isDeleted())
                .pinned(c.isPinned())
                .pinnedAt(c.getPinnedAt())
                .replyCount(c.getReplyCount())
                .authorId(c.isDeleted() ? null : (c.getUser() == null ? null : c.getUser().getId()))
                .authorName(c.isDeleted() ? null : (c.getUser() == null ? null : c.getUser().getName()))
                .authorProfileImage(c.isDeleted() ? null : (c.getUser() == null ? null : c.getUser().getProfileImage()))
                .authorVerified(!c.isDeleted() && c.getUser() != null && c.getUser().isVerified())
                .authorAccountType(c.isDeleted() ? null : (c.getUser() == null ? null : c.getUser().getAccountType()))
                .reactions(reactionCounts)
                .reactionsTotal(total)
                .likesCount(likeCount)
                .myReaction(myReaction)
                .likedByMe(myReaction != null && myReaction.equals("LIKE"))
                .mentions(mentionDtos)
                .byPostOwner(byPostOwner && !c.isDeleted())
                .build();
    }

    private boolean isPostOwner(Comment c, Post post) {
        if (post == null || c.getUser() == null) return false;
        return post.getUser() != null
                && post.getUser().getId().equals(c.getUser().getId());
    }

    private Map<String, Long> aggregateReactions(Long commentId) {
        Map<String, Long> m = new HashMap<>();
        for (Object[] r : reactions.countsByEmoji(commentId)) {
            m.put(((CommentReactionType) r[0]).name(),
                    ((Number) r[1]).longValue());
        }
        return m;
    }

    private String myReactionFor(Long commentId, User viewer) {
        if (viewer == null) return null;
        return reactions.findByCommentIdAndUserId(commentId, viewer.getId())
                .map(r -> r.getEmoji().name())
                .orElse(null);
    }

    /** Trim + clamp at 1000 chars. */
    private String sanitise(String s) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() > 1000) t = t.substring(0, 1000);
        return t;
    }

    private String safeTrim(String s, int max) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.length() > max ? t.substring(0, max) : t;
    }

    private User viewerOf(User author) {
        // Author always sees their own reaction state — even if empty.
        return author;
    }
}
