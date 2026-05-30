package com.example.demo.controller;

import com.example.demo.entity.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Stories 2.0 surface — polls, reactions, highlights, close friends.
 * The base CRUD for stories themselves lives in the existing StoryController;
 * this file owns only the new interactive features so we don't disturb the
 * battle-tested code.
 *
 * <ul>
 *   <li>{@code POST   /stories/{id}/poll              — attach poll to a story</li>
 *   <li>{@code POST   /stories/poll/{pollId}/vote     — vote A / B</li>
 *   <li>{@code POST   /stories/{id}/react             — emoji reaction</li>
 *   <li>{@code GET    /stories/{id}/reactions         — count by emoji</li>
 *   <li>{@code GET    /stories/highlights/{userId}    — list highlights</li>
 *   <li>{@code POST   /stories/highlights             — create highlight</li>
 *   <li>{@code POST   /stories/highlights/{id}/add    — add story to highlight</li>
 *   <li>{@code GET    /stories/close-friends          — list my close friends</li>
 *   <li>{@code POST   /stories/close-friends/{id}     — add</li>
 *   <li>{@code DELETE /stories/close-friends/{id}     — remove</li>
 * </ul>
 */
@RestController
@RequestMapping("/stories")
@RequiredArgsConstructor
public class StoriesV2Controller {

    private final StoryRepository stories;
    private final StoryPollRepository polls;
    private final StoryPollVoteRepository pollVotes;
    private final StoryReactionRepository reactions;
    private final StoryHighlightRepository highlights;
    private final StoryHighlightItemRepository highlightItems;
    private final CloseFriendRepository closeFriends;
    private final UserRepository users;

    // ============================================================ polls
    @PostMapping("/{storyId}/poll")
    public StoryPoll createPoll(@PathVariable Long storyId,
                                @RequestBody StoryPoll body) {
        body.setStoryId(storyId);
        body.setVotesA(0); body.setVotesB(0);
        return polls.save(body);
    }

    @PostMapping("/poll/{pollId}/vote")
    public Map<String, Object> vote(@PathVariable Long pollId,
                                    @RequestParam String choice,
                                    Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) throw new SecurityException("Auth required");

        StoryPoll p = polls.findById(pollId).orElseThrow(() -> new RuntimeException("Poll"));

        if (pollVotes.findByPollIdAndUserId(pollId, uid).isPresent()) {
            return Map.of("status", "already_voted", "votesA", p.getVotesA(), "votesB", p.getVotesB());
        }

        String c = "A".equalsIgnoreCase(choice) ? "A" : "B";
        pollVotes.save(StoryPollVote.builder()
                .pollId(pollId).userId(uid).choice(c).build());
        if ("A".equals(c)) polls.bumpA(pollId); else polls.bumpB(pollId);

        StoryPoll updated = polls.findById(pollId).orElse(p);
        return Map.of("status", "ok", "votesA", updated.getVotesA(), "votesB", updated.getVotesB());
    }

    // ============================================================ reactions
    @PostMapping("/{storyId}/react")
    public Map<String, Object> react(@PathVariable Long storyId,
                                     @RequestParam String emoji,
                                     Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) throw new SecurityException("Auth required");
        reactions.save(StoryReaction.builder()
                .storyId(storyId).userId(uid).emoji(emoji.trim()).build());

        Story s = stories.findById(storyId).orElseThrow(() -> new RuntimeException("Story"));
        s.setReactionCount(s.getReactionCount() + 1);
        stories.save(s);
        return Map.of("status", "ok");
    }

    @GetMapping("/{storyId}/reactions")
    public List<Map<String, Object>> reactionSummary(@PathVariable Long storyId) {
        return reactions.summary(storyId);
    }

    // ============================================================ highlights
    @GetMapping("/highlights/{userId}")
    public List<StoryHighlight> listHighlights(@PathVariable Long userId) {
        return highlights.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @PostMapping("/highlights")
    public StoryHighlight createHighlight(@RequestBody Map<String, String> body, Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) throw new SecurityException("Auth required");
        return highlights.save(StoryHighlight.builder()
                .userId(uid)
                .title(body.getOrDefault("title", "Highlight"))
                .coverUrl(body.get("coverUrl"))
                .createdAt(LocalDateTime.now())
                .build());
    }

    @PostMapping("/highlights/{id}/add")
    public StoryHighlightItem addToHighlight(@PathVariable Long id,
                                             @RequestParam Long storyId,
                                             @RequestParam(defaultValue = "0") int order) {
        return highlightItems.save(StoryHighlightItem.builder()
                .highlightId(id).storyId(storyId).sortOrder(order).build());
    }

    @GetMapping("/highlights/{id}/items")
    public List<StoryHighlightItem> highlightItems(@PathVariable Long id) {
        return highlightItems.findByHighlightIdOrderBySortOrderAsc(id);
    }

    // ============================================================ close friends
    @GetMapping("/close-friends")
    public List<Map<String, Object>> myCloseFriends(Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) return List.of();
        List<CloseFriend> rows = closeFriends.findByUserId(uid);
        return rows.stream().map(cf -> {
            Map<String, Object> m = new HashMap<>();
            m.put("friendId", cf.getFriendId());
            users.findById(cf.getFriendId()).ifPresent(u -> {
                m.put("name", u.getName());
                m.put("profileImage", u.getProfileImage());
            });
            return m;
        }).toList();
    }

    @PostMapping("/close-friends/{friendId}")
    public Map<String, Object> addCloseFriend(@PathVariable Long friendId, Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) throw new SecurityException("Auth required");
        if (uid.equals(friendId)) throw new IllegalArgumentException("Can't add yourself");
        if (!closeFriends.existsByUserIdAndFriendId(uid, friendId)) {
            closeFriends.save(CloseFriend.builder().userId(uid).friendId(friendId).build());
        }
        return Map.of("status", "ok");
    }

    @DeleteMapping("/close-friends/{friendId}")
    public Map<String, Object> removeCloseFriend(@PathVariable Long friendId, Authentication auth) {
        Long uid = uid(auth);
        if (uid == null) throw new SecurityException("Auth required");
        closeFriends.delete(uid, friendId);
        return Map.of("status", "ok");
    }

    private Long uid(Authentication auth) {
        if (auth == null) return null;
        return users.findByEmail(auth.getName()).map(User::getId).orElse(null);
    }
}
