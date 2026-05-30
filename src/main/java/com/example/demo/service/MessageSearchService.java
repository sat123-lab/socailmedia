package com.example.demo.service;

import com.example.demo.entity.ChatMessage;
import com.example.demo.entity.Conversation;
import com.example.demo.repository.ChatMessageRepository;
import com.example.demo.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Message search — respects E2EE by only matching plaintext rows.
 * Encrypted ciphertext is never searched server-side.
 */
@Service
@RequiredArgsConstructor
public class MessageSearchService {

    private final ChatMessageRepository messages;
    private final ConversationRepository conversations;

    public Map<String, Object> search(Long userId, String query, Long conversationId,
                                      String mediaKind, int page, int size) {
        int pageSize = Math.min(Math.max(size, 1), 50);
        PageRequest pr = PageRequest.of(Math.max(page, 0), pageSize);
        String q = query == null ? "" : query.trim();

        Page<ChatMessage> result;
        if (conversationId != null) {
            assertParticipant(userId, conversationId);
            if (q.isEmpty() && mediaKind == null) {
                result = messages.findPageByConversation(conversationId, pr);
            } else {
                result = messages.searchInConversation(
                        conversationId, q, mediaKind, pr);
            }
        } else {
            if (q.length() < 2) {
                throw new IllegalArgumentException("Query too short");
            }
            result = messages.searchGlobalForUser(userId, q, pr);
        }

        List<Map<String, Object>> items = result.getContent().stream()
                .map(this::toHit)
                .toList();

        Map<String, Object> out = new HashMap<>();
        out.put("items", items);
        out.put("page", result.getNumber());
        out.put("totalPages", result.getTotalPages());
        out.put("totalElements", result.getTotalElements());
        out.put("hasMore", result.hasNext());
        return out;
    }

    private Map<String, Object> toHit(ChatMessage m) {
        Map<String, Object> h = new HashMap<>();
        h.put("id", m.getId());
        h.put("conversationId", m.getConversation() == null ? null : m.getConversation().getId());
        h.put("senderId", m.getSender() == null ? null : m.getSender().getId());
        h.put("encrypted", m.isEncrypted());
        h.put("mediaKind", m.getMediaKind());
        h.put("messageType", m.getMessageType());
        h.put("replyToId", m.getReplyToId());
        h.put("createdAt", m.getCreatedAt() == null ? null : m.getCreatedAt().toString());
        // Never return ciphertext in search results for encrypted messages.
        if (m.isEncrypted()) {
            h.put("preview", "[encrypted message]");
        } else if (m.isDeletedForEveryone()) {
            h.put("preview", "[deleted]");
        } else {
            String c = m.getContent();
            h.put("preview", c == null ? "" : (c.length() > 120 ? c.substring(0, 120) + "…" : c));
        }
        return h;
    }

    private void assertParticipant(Long userId, Long conversationId) {
        Conversation c = conversations.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        boolean ok = (c.getUserOne() != null && c.getUserOne().getId().equals(userId))
                || (c.getUserTwo() != null && c.getUserTwo().getId().equals(userId));
        if (!ok) throw new SecurityException("Not a participant");
    }
}
