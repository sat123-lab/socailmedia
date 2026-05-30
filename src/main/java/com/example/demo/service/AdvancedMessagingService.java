package com.example.demo.service;

import com.example.demo.dto.MessageReactionsDto;
import com.example.demo.dto.SendMessageRequest;
import com.example.demo.entity.*;
import com.example.demo.messaging.MessagingAIService;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reactions, forwards, view-once consumption — the V6 messaging features
 * that sit alongside (not inside) the legacy {@link MessageService} send path.
 */
@Service
@RequiredArgsConstructor
public class AdvancedMessagingService {

    /** WhatsApp-style cap on pinned chats per user. */
    public static final int MAX_PINS = 5;
    /** Telegram-style bulk-forward safety limit. */
    public static final int MAX_FORWARD_TARGETS = 10;

    public static final Set<String> ALLOWED_EMOJI = Set.of(
            "❤️", "😂", "😮", "😢", "😡", "👍", "👎", "🔥", "🎉"
    );

    private final MessageReactionRepository reactions;
    private final ChatMessageRepository messages;
    private final RealtimeEventService realtime;
    private final MessageService messageService;
    private final MessagingAIService ai;

    // ============================================================
    //  REACTIONS
    // ============================================================

    @Transactional
    public MessageReactionsDto toggleReaction(Long messageId, Long userId, String emoji) {
        if (!ALLOWED_EMOJI.contains(emoji)) {
            throw new IllegalArgumentException("Emoji not allowed");
        }
        ChatMessage msg = requireMessage(messageId);
        assertParticipant(msg, userId);

        var existing = reactions.findByMessageIdAndUserIdAndEmoji(messageId, userId, emoji);
        if (existing.isPresent()) {
            reactions.delete(existing.get());
        } else {
            reactions.save(MessageReaction.builder()
                    .messageId(messageId)
                    .userId(userId)
                    .emoji(emoji)
                    .build());
        }

        MessageReactionsDto dto = buildReactionsDto(messageId, userId);
        fanoutReaction(msg, dto, existing.isPresent() ? "removed" : "added", userId, emoji);
        return dto;
    }

    public MessageReactionsDto reactionsForMessage(Long messageId, Long viewerId) {
        return buildReactionsDto(messageId, viewerId);
    }

    public Map<Long, MessageReactionsDto> reactionsForMessages(
            Collection<Long> messageIds, Long viewerId) {
        if (messageIds == null || messageIds.isEmpty()) return Map.of();
        List<MessageReaction> rows = reactions.findByMessageIds(messageIds);
        Map<Long, List<MessageReaction>> grouped = rows.stream()
                .collect(Collectors.groupingBy(MessageReaction::getMessageId));
        Map<Long, MessageReactionsDto> out = new LinkedHashMap<>();
        for (Long id : messageIds) {
            out.put(id, MessageReactionsDto.build(
                    id, grouped.getOrDefault(id, List.of()), viewerId));
        }
        return out;
    }

    // ============================================================
    //  FORWARD
    // ============================================================

    @Transactional
    public List<ChatMessage> forward(Long actorId, Long originMessageId,
                                    List<Long> targetUserIds, String actorEmail) {
        if (targetUserIds == null || targetUserIds.isEmpty()) {
            throw new IllegalArgumentException("No targets");
        }
        if (targetUserIds.size() > MAX_FORWARD_TARGETS) {
            throw new IllegalArgumentException("Too many forward targets");
        }

        ChatMessage origin = requireMessage(originMessageId);
        assertParticipant(origin, actorId);

        // ---- E2EE boundary --------------------------------------------------
        // The server is NOT allowed to "trans-encrypt" — the ECDH derivation
        // used the original recipient's identity key + their pre-key id, so
        // copying the ciphertext to a different user would deliver a payload
        // that recipient cannot decrypt. Clients must re-encrypt locally and
        // POST /messages/send for each forward target instead.
        if (origin.isEncrypted()) {
            throw new IllegalStateException(
                "Cannot forward an end-to-end encrypted message server-side. "
              + "Re-encrypt and send via /messages/send per recipient.");
        }

        // Optional AI spam gate on plaintext previews only.
        if (origin.getContent() != null) {
            ai.spam().ifPresent(p -> {
                if (p.isSpam(actorId, origin.getContent())) {
                    throw new SecurityException("Forward blocked by spam policy");
                }
            });
        }

        List<ChatMessage> created = new ArrayList<>();
        for (Long targetId : new LinkedHashSet<>(targetUserIds)) {
            if (targetId.equals(actorId)) continue;
            SendMessageRequest req = new SendMessageRequest();
            req.setReceiverId(targetId);
            req.setContent(origin.getContent());
            req.setPostId(origin.getPostId());
            // Forwards always plaintext — see E2EE guard above.
            req.setEncrypted(false);
            req.setForwardOriginId(originMessageId);
            req.setMediaKind(origin.getMediaKind());
            req.setVoiceWaveform(origin.getVoiceWaveform());
            req.setVoiceDurationMs(origin.getVoiceDurationMs());
            req.setViewOnce(false); // forwards are never view-once
            ChatMessage copy = messageService.sendMessage(actorEmail, req);
            created.add(copy);

            Map<String, Object> payload = new HashMap<>();
            payload.put("messageId", copy.getId());
            payload.put("originId", originMessageId);
            payload.put("conversationId", copy.getConversation().getId());
            realtime.toUser(targetId, RealtimeEventService.TYPE_MESSAGE_FORWARDED, payload);
        }
        return created;
    }

    // ============================================================
    //  VIEW ONCE
    // ============================================================

    @Transactional
    public ChatMessage consumeViewOnce(Long messageId, Long viewerId) {
        ChatMessage m = requireMessage(messageId);
        assertParticipant(m, viewerId);
        if (!m.isViewOnce()) {
            throw new IllegalStateException("Not a view-once message");
        }
        if (m.isViewOnceConsumed()) {
            throw new IllegalStateException("Already consumed");
        }
        // Only the non-sender may consume.
        if (m.getSender() != null && m.getSender().getId().equals(viewerId)) {
            return m;
        }

        m.setViewOnceConsumed(true);
        m.setContent("[View once media — opened]");
        m.setReadAt(LocalDateTime.now());
        messages.save(m);

        Long senderId = m.getSender() == null ? null : m.getSender().getId();
        Map<String, Object> payload = Map.of(
                "messageId", messageId,
                "conversationId", m.getConversation().getId());
        if (senderId != null) {
            realtime.toUser(senderId, RealtimeEventService.TYPE_MESSAGE_VIEW_ONCE, payload);
        }
        realtime.toUser(viewerId, RealtimeEventService.TYPE_MESSAGE_VIEW_ONCE, payload);
        return m;
    }

    // ============================================================
    //  helpers
    // ============================================================

    private MessageReactionsDto buildReactionsDto(long messageId, Long viewerId) {
        return MessageReactionsDto.build(
                messageId, reactions.findByMessageId(messageId), viewerId);
    }

    private void fanoutReaction(ChatMessage msg, MessageReactionsDto dto,
                                String action, Long actorId, String emoji) {
        Long convId = msg.getConversation().getId();
        Conversation c = msg.getConversation();
        Set<Long> peers = new HashSet<>();
        if (c.getUserOne() != null) peers.add(c.getUserOne().getId());
        if (c.getUserTwo() != null) peers.add(c.getUserTwo().getId());

        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", msg.getId());
        payload.put("conversationId", convId);
        payload.put("action", action);
        payload.put("emoji", emoji);
        payload.put("actorId", actorId);
        payload.put("counts", dto.counts);
        payload.put("myReactions", dto.myReactions);

        for (Long uid : peers) {
            realtime.toUser(uid, RealtimeEventService.TYPE_MESSAGE_REACTION, payload);
        }
    }

    private ChatMessage requireMessage(Long id) {
        return messages.findById(id)
                .orElseThrow(() -> new RuntimeException("Message not found"));
    }

    private void assertParticipant(ChatMessage m, Long userId) {
        Conversation c = m.getConversation();
        if (c == null) throw new SecurityException("No conversation");
        boolean ok = (c.getUserOne() != null && c.getUserOne().getId().equals(userId))
                || (c.getUserTwo() != null && c.getUserTwo().getId().equals(userId));
        if (!ok) throw new SecurityException("Not a participant");
    }
}
