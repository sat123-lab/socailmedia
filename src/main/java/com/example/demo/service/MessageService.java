package com.example.demo.service;

import com.example.demo.dto.ConversationDto;
import com.example.demo.entity.ChatMessage;
import com.example.demo.entity.Conversation;
import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.repository.ChatMessageRepository;
import com.example.demo.repository.ConversationRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    private final NotificationRepository notificationRepository;
    private final RealtimeEventService realtimeEventService;

    public void updatePresence(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setLastSeenAt(LocalDateTime.now());
        userRepository.save(user);
    }

    public List<ConversationDto> getConversations(String email) {
        User current = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Conversation> conversations =
                conversationRepository.findAllForUser(current);

        List<ConversationDto> result = new ArrayList<>();

        for (Conversation conversation : conversations) {
            User other = conversation.getUserOne().getId().equals(current.getId())
                    ? conversation.getUserTwo()
                    : conversation.getUserOne();

            ChatMessage last = chatMessageRepository
                    .findFirstByConversationOrderByCreatedAtDesc(conversation);

            String preview = last != null ? formatMessagePreview(last) : "";
            Long postId = last != null ? last.getPostId() : null;
            LocalDateTime lastAt = last != null
                    ? last.getCreatedAt()
                    : conversation.getUpdatedAt();

            result.add(
                    ConversationDto.builder()
                            .conversationId(conversation.getId())
                            .otherUserId(other.getId())
                            .otherUserName(other.getName())
                            .lastMessage(preview)
                            .sharedPostId(postId)
                            .lastMessageAt(lastAt)
                            .online(isOnline(other))
                            .lastSeenText(formatLastSeen(other))
                            .build()
            );
        }

        return result;
    }

    public List<ChatMessage> getMessages(String email, Long otherUserId) {
        User current = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        User other = userRepository.findById(otherUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Conversation conversation = getOrCreateConversation(current, other);

        return chatMessageRepository
                .findByConversationOrderByCreatedAtAsc(conversation);
    }

    public ChatMessage sendMessage(
            String email,
            Long receiverId,
            String content,
            Long postId
    ) {
        com.example.demo.dto.SendMessageRequest legacy = new com.example.demo.dto.SendMessageRequest();
        legacy.setReceiverId(receiverId);
        legacy.setContent(content);
        legacy.setPostId(postId);
        return sendMessage(email, legacy);
    }

    /**
     * Full-featured send — accepts the encrypted envelope + disappearing-message
     * timer. The legacy 4-arg overload above stays for callers we don't want to
     * touch yet (e.g. call-log writers).
     */
    public ChatMessage sendMessage(String email, com.example.demo.dto.SendMessageRequest req) {

        User sender = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        User receiver = userRepository.findById(req.getReceiverId())
                .orElseThrow(() -> new RuntimeException("Receiver not found"));

        Conversation conversation = getOrCreateConversation(sender, receiver);

        String messageContent = req.getContent() != null && !req.getContent().isBlank()
                ? req.getContent().trim()
                : (req.getPostId() != null ? "Shared a post" : "");

        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .sender(sender)
                .content(messageContent)
                .postId(req.getPostId())
                // Envelope encryption metadata
                .encrypted(Boolean.TRUE.equals(req.getEncrypted()))
                .encryptionAlgo(req.getEncryptionAlgo())
                .senderEphemeralKey(req.getSenderEphemeralKey())
                .recipientPreKeyId(req.getRecipientPreKeyId())
                .nonce(req.getNonce())
                // Disappearing TTL — expiresAt is set lazily on first read
                .expiresInSeconds(req.getExpiresInSeconds())
                // V6 — reply / forward / view-once / voice metadata
                .replyToId(req.getReplyToId())
                .forwardOriginId(req.getForwardOriginId())
                .viewOnce(Boolean.TRUE.equals(req.getViewOnce()))
                .voiceWaveform(req.getVoiceWaveform())
                .voiceDurationMs(req.getVoiceDurationMs())
                .mediaKind(req.getMediaKind())
                .build();

        if (req.getForwardOriginId() != null) {
            chatMessageRepository.findById(req.getForwardOriginId()).ifPresent(origin -> {
                origin.setForwardCount(origin.getForwardCount() + 1);
                chatMessageRepository.save(origin);
            });
        }

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);

        sender.setLastSeenAt(LocalDateTime.now());
        userRepository.save(sender);

        ChatMessage saved = chatMessageRepository.save(message);

        Notification notification = Notification.builder()
                .receiver(receiver)
                .sender(sender)
                .type("MESSAGE")
                .message(sender.getName() + " sent you a message")
                .relatedId(sender.getId())
                .read(false)
                .build();

        notificationRepository.save(notification);

        // Realtime fan-out to the admin Private-Chats Monitor — REDACTED payload only.
        // We send no content, no nonce, no ephemeral key — just metadata so the
        // dashboard can prepend the new row in real time.
        try {
            java.util.Map<String, Object> meta = new java.util.HashMap<>();
            meta.put("id", saved.getId());
            meta.put("kind", "DIRECT");
            meta.put("senderId", sender.getId());
            meta.put("senderName", sender.getName());
            meta.put("receiverId", receiver.getId());
            meta.put("receiverName", receiver.getName());
            meta.put("encrypted", saved.isEncrypted());
            meta.put("encryptionAlgo", saved.getEncryptionAlgo());
            meta.put("encryptedLength", messageContent.length());
            meta.put("createdAt", String.valueOf(saved.getCreatedAt()));
            realtimeEventService.toPrivateChatsAdmin(meta);
        } catch (Exception ignored) { /* never break sends on telemetry */ }

        return saved;
    }

    public void saveCallLog(
            User actor,
            User other,
            String callType,
            String callStatus,
            Integer durationSeconds
    ) {
        Conversation conversation = getOrCreateConversation(actor, other);

        // One "line busy" bubble per chat per 2 minutes — repeated taps
        // should not flood the thread.
        if ("BUSY".equals(callStatus)) {
            var since = java.time.LocalDateTime.now().minusMinutes(2);
            if (chatMessageRepository
                    .existsByConversationAndMessageTypeAndCallStatusAndCreatedAtAfter(
                            conversation, "CALL", "BUSY", since)) {
                return;
            }
        }

        String content = buildCallLogText(callType, callStatus, durationSeconds, actor, other);

        ChatMessage message = ChatMessage.builder()
                .conversation(conversation)
                .sender(actor)
                .content(content)
                .messageType("CALL")
                .callType(callType)
                .callStatus(callStatus)
                .callDurationSeconds(durationSeconds)
                .build();

        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
        chatMessageRepository.save(message);
    }

    private String buildCallLogText(
            String callType,
            String callStatus,
            Integer durationSeconds,
            User actor,
            User other
    ) {
        String media = "VIDEO".equals(callType) ? "Video call" : "Voice call";
        return switch (callStatus) {
            case "INCOMING" -> "Incoming " + media.toLowerCase();
            case "ANSWERED" -> {
                if (durationSeconds != null && durationSeconds > 0) {
                    int m = durationSeconds / 60;
                    int s = durationSeconds % 60;
                    yield media + " · " + String.format("%d:%02d", m, s);
                }
                yield media;
            }
            case "MISSED" -> "Missed " + media.toLowerCase();
            case "DECLINED" -> media + " declined";
            case "CANCELLED" -> media + " cancelled";
            case "BUSY" -> media + " · line busy";
            default -> media;
        };
    }

    private String formatMessagePreview(ChatMessage message) {
        if ("CALL".equals(message.getMessageType())) {
            return message.getContent();
        }
        return message.getContent();
    }

    private Conversation getOrCreateConversation(User a, User b) {
        return conversationRepository.findBetweenUsers(a, b)
                .orElseGet(() -> {
                    User userOne = a.getId() < b.getId() ? a : b;
                    User userTwo = a.getId() < b.getId() ? b : a;

                    Conversation conversation = Conversation.builder()
                            .userOne(userOne)
                            .userTwo(userTwo)
                            .updatedAt(LocalDateTime.now())
                            .build();

                    return conversationRepository.save(conversation);
                });
    }

    private boolean isOnline(User user) {
        if (user.getLastSeenAt() == null) {
            return false;
        }
        return Duration.between(user.getLastSeenAt(), LocalDateTime.now())
                .toMinutes() < 2;
    }

    private String formatLastSeen(User user) {
        if (user.getLastSeenAt() == null) {
            return "Offline";
        }
        if (isOnline(user)) {
            return "Active now";
        }

        Duration diff = Duration.between(user.getLastSeenAt(), LocalDateTime.now());

        if (diff.toMinutes() < 60) {
            return "Active " + diff.toMinutes() + "m ago";
        }
        if (diff.toHours() < 24) {
            return "Active " + diff.toHours() + "h ago";
        }
        if (diff.toDays() < 7) {
            return "Active " + diff.toDays() + "d ago";
        }

        return "Active on " + user.getLastSeenAt()
                .format(DateTimeFormatter.ofPattern("MMM d"));
    }
}
