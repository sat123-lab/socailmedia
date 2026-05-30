package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "conversation_id")
    @JsonIgnoreProperties({"userOne", "userTwo"})
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sender_id")
    @JsonIgnoreProperties({
            "password", "posts", "comments", "likes", "followers", "following"
    })
    private User sender;

    @Column(length = 2000)
    private String content;

    // ---- Envelope encryption fields (added in the security baseline) ----
    // When `encrypted` is true the client has already encrypted `content` end-to-end.
    // The server stores opaque ciphertext + the metadata needed for the recipient to
    // derive the key. The server CANNOT decrypt these fields.

    /** True when {@code content} holds ciphertext (base64). */
    @Column(nullable = false)
    @lombok.Builder.Default
    private boolean encrypted = false;

    /** Algorithm tag, e.g. "X25519-AESGCM-V1". */
    @Column(length = 32)
    private String encryptionAlgo;

    /** Sender's ephemeral X25519 public key (base64) — needed for recipient ECDH. */
    @Column(length = 88)
    private String senderEphemeralKey;

    /** Recipient pre-key id consumed for this message (so client knows which secret to use). */
    @Column(length = 36)
    private String recipientPreKeyId;

    /** AES-GCM nonce (base64) — 12 random bytes per message. */
    @Column(length = 24)
    private String nonce;

    // ---- Disappearing / deletion lifecycle ----

    /** TTL in seconds requested by the sender (10, 30, 60, 3600, 86400 …). null = never. */
    private Integer expiresInSeconds;

    /** Wall-clock expiry, set on first read receipt. */
    private LocalDateTime expiresAt;

    /** When the receiver opened the conversation containing this message. */
    private LocalDateTime readAt;

    /** True when sender invoked "delete for everyone". */
    @Column(nullable = false)
    @lombok.Builder.Default
    private boolean deletedForEveryone = false;

    private LocalDateTime deletedAt;

    /** Audit-safe tombstone summary written when content is wiped. */
    @Column(length = 64)
    private String deletionReason;

    private Long postId;

    // ---- Reply / forward / view-once / voice metadata -------------------
    // (V6) — additions are NULL-safe so legacy rows still load.

    /** Parent message id when this message is a reply. Indexed in V6. */
    @Column(name = "reply_to_id")
    private Long replyToId;

    /** How many times this message has been forwarded. Bumped server-side
     *  when {@code forward_origin_id} of a new message points at this row. */
    @Column(name = "forward_count", nullable = false)
    @lombok.Builder.Default
    private int forwardCount = 0;

    /** id of the original message when this message was forwarded. */
    @Column(name = "forward_origin_id")
    private Long forwardOriginId;

    /** Disappearing media — destroyed after first read. */
    @Column(name = "view_once", nullable = false)
    @lombok.Builder.Default
    private boolean viewOnce = false;

    /** Set true the moment the receiver opens the chat with a view-once
     *  message; the lifecycle job then wipes content on the next tick. */
    @Column(name = "view_once_consumed", nullable = false)
    @lombok.Builder.Default
    private boolean viewOnceConsumed = false;

    /** Base64 / comma-separated normalized peak heights for voice notes.
     *  Stored client-side as a compact string; never decoded on the server. */
    @Column(name = "voice_waveform", length = 2048)
    private String voiceWaveform;

    @Column(name = "voice_duration_ms")
    private Integer voiceDurationMs;

    /** Optional UI hint — TEXT | IMAGE | VIDEO | VOICE | FILE | LINK. */
    @Column(name = "media_kind", length = 16)
    private String mediaKind;

    // TEXT or CALL
    @lombok.Builder.Default
    private String messageType = "TEXT";

    // VOICE or VIDEO (for CALL)
    private String callType;

    // MISSED, ANSWERED, DECLINED, CANCELLED, BUSY, INCOMING
    private String callStatus;

    private Integer callDurationSeconds;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
