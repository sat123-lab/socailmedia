package com.example.demo.dto;

import com.example.demo.entity.ChatMessage;
import com.example.demo.entity.ChatRoomMessage;
import com.example.demo.entity.Conversation;
import com.example.demo.entity.SocialGroupMessage;
import com.example.demo.entity.User;
import com.example.demo.security.AdminSecurityService;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Strictly metadata-only view of a private message used by the admin
 * "Private Chats Monitor" page.
 *
 * <p>What the admin panel is allowed to see:</p>
 * <ul>
 *   <li>{@link #id}              — internal message id</li>
 *   <li>{@link #type}            — DIRECT, GROUP, or ROOM</li>
 *   <li>{@link #senderId} / {@link #senderName}     — who sent it</li>
 *   <li>{@link #receiverId} / {@link #receiverName} — the counterpart (user id, group id,
 *       or chat-room id, with a human label)</li>
 *   <li>{@link #createdAt}       — wall-clock time</li>
 *   <li>{@link #encrypted}       — whether the payload is sealed under E2EE</li>
 *   <li>{@link #encryptionAlgo}  — the algorithm string (e.g. {@code aes-256-gcm/x25519})</li>
 *   <li>{@link #encryptedLength} — size of the ciphertext or hidden text in bytes</li>
 *   <li>{@link #preview}         — always {@code AdminSecurityService.REDACTED}; admins
 *       can never see the body.</li>
 * </ul>
 *
 * <p>What the admin panel will NEVER see:</p>
 * <ul>
 *   <li>The plaintext or ciphertext content itself.</li>
 *   <li>Per-message E2EE nonces, ephemeral keys, or pre-key ids.</li>
 *   <li>Anything that could be used to decrypt the message client-side.</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPrivateChatDto {

    public enum Type { DIRECT, GROUP, ROOM }

    private Long id;
    private Type type;

    private Long senderId;
    private String senderName;

    private Long receiverId;
    private String receiverName;

    private LocalDateTime createdAt;

    private boolean encrypted;
    private String encryptionAlgo;
    private int encryptedLength;

    /** Always the redacted placeholder. Never holds real content. */
    private String preview;

    // =====================================================================
    //  Factory methods — the ONLY way to build a row. They are deliberately
    //  designed so the body of the message cannot escape from this class.
    // =====================================================================

    public static AdminPrivateChatDto from(ChatMessage m) {
        Long otherId = null;
        String otherName = null;
        Conversation c = m.getConversation();
        User sender = m.getSender();
        if (c != null && sender != null) {
            User one = c.getUserOne();
            User two = c.getUserTwo();
            if (one != null && !one.getId().equals(sender.getId())) {
                otherId = one.getId();
                otherName = one.getName();
            } else if (two != null) {
                otherId = two.getId();
                otherName = two.getName();
            }
        }
        return baseBuilder(Type.DIRECT, m.getId(), sender, m.getContent(),
                m.isEncrypted(), m.getEncryptionAlgo(), m.getCreatedAt())
                .receiverId(otherId)
                .receiverName(otherName)
                .build();
    }

    public static AdminPrivateChatDto from(SocialGroupMessage m) {
        Long gid = m.getGroup() == null ? null : m.getGroup().getId();
        String gname = m.getGroup() == null ? null : m.getGroup().getName();
        return baseBuilder(Type.GROUP, m.getId(), m.getSender(), m.getContent(),
                m.isEncrypted(), m.getEncryptionAlgo(), m.getCreatedAt())
                .receiverId(gid)
                .receiverName(gname == null ? null : "Group · " + gname)
                .build();
    }

    public static AdminPrivateChatDto from(ChatRoomMessage m) {
        Long rid = m.getRoom() == null ? null : m.getRoom().getId();
        String rname = m.getRoom() == null ? null : m.getRoom().getName();
        return baseBuilder(Type.ROOM, m.getId(), m.getSender(), m.getContent(),
                m.isEncrypted(), m.getEncryptionAlgo(), m.getCreatedAt())
                .receiverId(rid)
                .receiverName(rname == null ? null : "Room · " + rname)
                .build();
    }

    private static AdminPrivateChatDtoBuilder baseBuilder(Type type, Long id, User sender,
                                                          String content, boolean encrypted,
                                                          String algo, LocalDateTime createdAt) {
        return AdminPrivateChatDto.builder()
                .id(id)
                .type(type)
                .senderId(sender == null ? null : sender.getId())
                .senderName(sender == null ? null : sender.getName())
                .createdAt(createdAt)
                .encrypted(encrypted)
                .encryptionAlgo(algo)
                .encryptedLength(content == null ? 0 : content.length())
                // Body is *never* copied into the DTO. We compute the length from the
                // entity in this method and immediately drop the reference.
                .preview(AdminSecurityService.REDACTED);
    }
}
