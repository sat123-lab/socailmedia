package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_room_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "room_id")
    @JsonIgnore
    private ChatRoom room;

    @ManyToOne
    @JoinColumn(name = "sender_id")
    @JsonIgnoreProperties({"password", "posts", "comments", "likes", "followers", "following"})
    private User sender;

    @Column(length = 2000)
    private String content;

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
        createdAt = LocalDateTime.now();
    }
}
