package com.example.demo.dto;

import lombok.Data;

@Data
public class SendMessageRequest {

    private Long receiverId;

    private String content;

    private Long postId;

    // ---- Optional E2EE envelope fields (forwarded from EncryptedChatService) ----
    private Boolean encrypted;
    private String encryptionAlgo;
    private String senderEphemeralKey;
    private String recipientPreKeyId;
    private String nonce;

    /** Disappearing-message TTL in seconds. null = stays until manually deleted. */
    private Integer expiresInSeconds;

    // ---- V6 advanced messaging metadata -----------------------------

    /** Reply threading — id of the parent message. */
    private Long replyToId;

    /** Forwarding — id of the original message being forwarded. */
    private Long forwardOriginId;

    /** View-once photo/video — destroyed after first read. */
    private Boolean viewOnce;

    /** Voice note waveform (base64 / comma-separated peaks). */
    private String voiceWaveform;

    private Integer voiceDurationMs;

    /** TEXT | IMAGE | VIDEO | VOICE | FILE | LINK */
    private String mediaKind;
}
