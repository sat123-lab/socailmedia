package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.entity.ChatMessage;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * V6 advanced messaging REST surface — reactions, forwards, organisation,
 * presence, and search. Sits alongside the legacy {@link MessageController}
 * so existing clients keep working unchanged.
 */
@RestController
@RequestMapping("/messaging")
@RequiredArgsConstructor
@CrossOrigin("*")
public class MessagingController {

    private final AdvancedMessagingService messaging;
    private final ConversationOrganizationService organize;
    private final MessagingPresenceService presence;
    private final MessageSearchService search;
    private final UserRepository users;

    // ===================== REACTIONS =================================

    @PostMapping("/messages/{id}/react")
    public MessageReactionsDto react(@PathVariable Long id,
                                     @RequestBody ReactBody body,
                                     Authentication auth) {
        return messaging.toggleReaction(id, requireUserId(auth), body.emoji);
    }

    @GetMapping("/messages/{id}/reactions")
    public MessageReactionsDto reactions(@PathVariable Long id, Authentication auth) {
        return messaging.reactionsForMessage(id, requireUserId(auth));
    }

    @GetMapping("/messages/reactions")
    public Map<Long, MessageReactionsDto> reactionsBatch(
            @RequestParam List<Long> ids, Authentication auth) {
        return messaging.reactionsForMessages(ids, requireUserId(auth));
    }

    // ===================== FORWARD ===================================

    @PostMapping("/messages/{id}/forward")
    public List<ChatMessage> forward(@PathVariable Long id,
                                       @RequestBody ForwardBody body,
                                       Authentication auth) {
        return messaging.forward(
                requireUserId(auth), id, body.targetUserIds, auth.getName());
    }

    // ===================== VIEW ONCE =================================

    @PostMapping("/messages/{id}/view-once/consume")
    public ChatMessage consumeViewOnce(@PathVariable Long id, Authentication auth) {
        return messaging.consumeViewOnce(id, requireUserId(auth));
    }

    // ===================== ORGANISATION =============================

    @GetMapping("/conversations/{convId}/settings")
    public ConversationSettingsDto settings(@PathVariable Long convId,
                                             Authentication auth) {
        return organize.getSettings(requireUserId(auth), convId);
    }

    @PostMapping("/conversations/{convId}/pin")
    public ConversationSettingsDto pin(@PathVariable Long convId, Authentication auth) {
        return organize.pin(requireUserId(auth), convId);
    }

    @PostMapping("/conversations/{convId}/unpin")
    public ConversationSettingsDto unpin(@PathVariable Long convId, Authentication auth) {
        return organize.unpin(requireUserId(auth), convId);
    }

    @PostMapping("/conversations/{convId}/archive")
    public ConversationSettingsDto archive(@PathVariable Long convId, Authentication auth) {
        return organize.archive(requireUserId(auth), convId);
    }

    @PostMapping("/conversations/{convId}/unarchive")
    public ConversationSettingsDto unarchive(@PathVariable Long convId, Authentication auth) {
        return organize.unarchive(requireUserId(auth), convId);
    }

    @PostMapping("/conversations/{convId}/mute")
    public ConversationSettingsDto mute(@PathVariable Long convId,
                                         @RequestBody MuteBody body,
                                         Authentication auth) {
        Instant until = body.mutedUntilMinutes == null ? null
                : Instant.now().plusSeconds(body.mutedUntilMinutes * 60L);
        return organize.mute(requireUserId(auth), convId, until);
    }

    @PostMapping("/conversations/pins/reorder")
    public ResponseEntity<Map<String, Object>> reorderPins(
            @RequestBody ReorderPinsBody body, Authentication auth) {
        organize.reorderPins(requireUserId(auth), body.orderedConversationIds);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/conversations/pinned")
    public List<ConversationSettingsDto> pinned(Authentication auth) {
        return organize.pinned(requireUserId(auth));
    }

    @GetMapping("/conversations/archived")
    public List<ConversationSettingsDto> archived(Authentication auth) {
        return organize.archived(requireUserId(auth));
    }

    // ===================== FOLDERS ===================================

    @GetMapping("/folders")
    public List<ChatFolderDto> folders(Authentication auth) {
        return organize.listFolders(requireUserId(auth));
    }

    @PostMapping("/folders")
    public ChatFolderDto createFolder(@RequestBody FolderBody body, Authentication auth) {
        return organize.createFolder(requireUserId(auth), body.name, body.emoji);
    }

    @DeleteMapping("/folders/{folderId}")
    public ResponseEntity<Map<String, Object>> deleteFolder(
            @PathVariable Long folderId, Authentication auth) {
        organize.deleteFolder(requireUserId(auth), folderId);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/conversations/{convId}/folder")
    public ConversationSettingsDto assignFolder(@PathVariable Long convId,
                                                   @RequestBody AssignFolderBody body,
                                                   Authentication auth) {
        return organize.assignFolder(requireUserId(auth), convId, body.folderId);
    }

    // ===================== PRESENCE ==================================

    @PostMapping("/presence/heartbeat")
    public PresenceDto heartbeat(Authentication auth) {
        return presence.heartbeat(requireUserId(auth));
    }

    @PostMapping("/presence/offline")
    public ResponseEntity<Map<String, Object>> offline(Authentication auth) {
        presence.goOffline(requireUserId(auth));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/presence/typing")
    public ResponseEntity<Map<String, Object>> typing(@RequestBody TypingBody body,
                                                       Authentication auth) {
        presence.setTyping(requireUserId(auth), body.conversationId, body.kind);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @PostMapping("/presence/typing/stop")
    public ResponseEntity<Map<String, Object>> stopTyping(Authentication auth) {
        presence.clearTyping(requireUserId(auth));
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/presence/{userId}")
    public PresenceDto userPresence(@PathVariable Long userId, Authentication auth) {
        return presence.get(userId, requireUserId(auth));
    }

    @GetMapping("/presence")
    public Map<Long, PresenceDto> batchPresence(@RequestParam List<Long> ids,
                                                 Authentication auth) {
        return presence.batch(ids, requireUserId(auth));
    }

    // ===================== SEARCH ====================================

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(required = false) String q,
                                       @RequestParam(required = false) Long conversationId,
                                       @RequestParam(required = false) String mediaKind,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "30") int size,
                                       Authentication auth) {
        return search.search(requireUserId(auth), q, conversationId, mediaKind, page, size);
    }

    // ===================== helpers ===================================

    private Long requireUserId(Authentication auth) {
        if (auth == null) throw new SecurityException("Not authenticated");
        return users.findByEmail(auth.getName()).map(User::getId)
                .orElseThrow(() -> new SecurityException("User not found"));
    }

    // ===================== request bodies ============================

    public static class ReactBody { public String emoji; }
    public static class ForwardBody { public List<Long> targetUserIds; }
    public static class MuteBody { public Integer mutedUntilMinutes; }
    public static class ReorderPinsBody { public List<Long> orderedConversationIds; }
    public static class FolderBody { public String name; public String emoji; }
    public static class AssignFolderBody { public Long folderId; }
    public static class TypingBody { public Long conversationId; public String kind; }
}
