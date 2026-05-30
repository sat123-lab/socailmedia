package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "social_group_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SocialGroupMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id")
    @JsonIgnoreProperties({"creator"})
    private SocialGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    @JsonIgnoreProperties({"password", "posts", "comments", "likes", "followers", "following"})
    private User sender;

    @Column(nullable = false, length = 2000)
    private String content;

    // Envelope encryption for group messages. Sender encrypts the message once per
    // recipient (fan-out) OR uses a shared group-key cached by every member after the
    // first secure handshake. See SECURITY.md for the trade-off chosen at runtime.
    @Column(nullable = false)
    @lombok.Builder.Default
    private boolean encrypted = false;

    @Column(length = 32)
    private String encryptionAlgo;

    @Column(length = 88)
    private String senderEphemeralKey;

    @Column(length = 24)
    private String nonce;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
