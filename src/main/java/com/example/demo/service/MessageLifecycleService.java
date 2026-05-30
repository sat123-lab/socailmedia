package com.example.demo.service;

import com.example.demo.entity.ChatMessage;
import com.example.demo.entity.User;
import com.example.demo.repository.ChatMessageRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles read receipts, disappearing-message expiry start, "delete for
 * everyone" tombstoning, and the scheduled purge of expired messages.
 *
 * <p>The expiry clock starts on the receiver's first read — not at send time —
 * which matches user intuition ("self-destructs N seconds after you see it").</p>
 *
 * <p>Realtime delete events are pushed through {@link RealtimeEventService} so
 * both client devices remove the bubble immediately. The corresponding DB row
 * is also wiped — there is no soft-tombstone for disappearing messages.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MessageLifecycleService {

    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final RealtimeEventService realtime;
    private final AuditLogService audit;

    @Autowired(required = false)
    private AdvancedMessagingService advancedMessaging;

    // ============================================================
    //  Read receipt — starts the expiry countdown
    // ============================================================
    @Transactional
    public ChatMessage markAsRead(Long messageId, String readerEmail) {
        ChatMessage m = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        User reader = userRepository.findByEmail(readerEmail).orElse(null);

        // View-once media is consumed on first open by the receiver.
        if (m.isViewOnce() && !m.isViewOnceConsumed() && advancedMessaging != null
                && reader != null && m.getSender() != null
                && !m.getSender().getId().equals(reader.getId())) {
            return advancedMessaging.consumeViewOnce(messageId, reader.getId());
        }

        if (m.getReadAt() == null) {
            m.setReadAt(LocalDateTime.now());
            if (m.getExpiresInSeconds() != null && m.getExpiresInSeconds() > 0) {
                m.setExpiresAt(m.getReadAt().plusSeconds(m.getExpiresInSeconds()));
            }
            messageRepository.save(m);

            // Tell sender's device the message has been read (drives the countdown UI).
            Long senderId = m.getSender() == null ? null : m.getSender().getId();
            if (senderId != null) {
                Map<String, Object> payload = new HashMap<>();
                payload.put("messageId", m.getId());
                payload.put("readAt", m.getReadAt().toString());
                payload.put("expiresAt", m.getExpiresAt() == null ? null : m.getExpiresAt().toString());
                payload.put("readerId", reader == null ? null : reader.getId());
                realtime.toUser(senderId, RealtimeEventService.TYPE_MESSAGE_READ, payload);
            }
        }
        return m;
    }

    // ============================================================
    //  Delete for everyone
    // ============================================================
    @Transactional
    public ChatMessage deleteForEveryone(Long messageId, String requesterEmail) {
        ChatMessage m = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));

        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new RuntimeException("Requester not found"));

        if (m.getSender() == null || !m.getSender().getId().equals(requester.getId())) {
            throw new SecurityException("Only the sender can delete for everyone.");
        }

        // Wipe content, set tombstone — keep the row for audit but make it look
        // identical to a baseline schema so admin redaction still works.
        m.setContent("");
        m.setEncrypted(false);
        m.setNonce(null);
        m.setSenderEphemeralKey(null);
        m.setRecipientPreKeyId(null);
        m.setDeletedForEveryone(true);
        m.setDeletedAt(LocalDateTime.now());
        m.setDeletionReason("DELETE_FOR_EVERYONE");
        messageRepository.save(m);

        broadcastDelete(m, RealtimeEventService.TYPE_MESSAGE_DELETED,
                "DELETE_FOR_EVERYONE");

        audit.record(AuditLogService.ADMIN_ACTION, requester.getId(),
                m.getId(), "ChatMessage", "delete-for-everyone", null);
        return m;
    }

    // ============================================================
    //  Scheduled purge — runs every 15 seconds
    // ============================================================
    @Scheduled(fixedDelay = 15_000L)
    @Transactional
    public void purgeExpired() {
        List<ChatMessage> due = messageRepository.findExpired(LocalDateTime.now());
        if (due.isEmpty()) return;

        for (ChatMessage m : due) {
            broadcastDelete(m, RealtimeEventService.TYPE_MESSAGE_EXPIRED, "EXPIRED");
        }
        List<Long> ids = due.stream().map(ChatMessage::getId).toList();
        int removed = messageRepository.deleteAllByIdInBulk(ids);
        log.info("Disappearing-message purge: removed {} rows.", removed);
    }

    // ============================================================
    //  Helpers
    // ============================================================
    private void broadcastDelete(ChatMessage m, String type, String reason) {
        Long sender = m.getSender() == null ? null : m.getSender().getId();
        Long conv = m.getConversation() == null ? null : m.getConversation().getId();

        Map<String, Object> payload = new HashMap<>();
        payload.put("messageId", m.getId());
        payload.put("conversationId", conv);
        payload.put("reason", reason);

        // Notify both ends of the conversation so the bubble disappears on both
        // devices in real time.
        if (sender != null) realtime.toUser(sender, type, payload);
        Long other = otherParticipant(m);
        if (other != null) realtime.toUser(other, type, payload);
    }

    private Long otherParticipant(ChatMessage m) {
        if (m.getConversation() == null) return null;
        Long senderId = m.getSender() == null ? null : m.getSender().getId();
        Long u1 = m.getConversation().getUserOne() == null ? null
                : m.getConversation().getUserOne().getId();
        Long u2 = m.getConversation().getUserTwo() == null ? null
                : m.getConversation().getUserTwo().getId();
        if (u1 != null && !u1.equals(senderId)) return u1;
        if (u2 != null && !u2.equals(senderId)) return u2;
        return null;
    }
}
