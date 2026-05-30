package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "chat_rooms")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private String emoji;

    @Column(unique = true, length = 32)
    private String inviteCode;

    @Builder.Default
    private boolean active = true;

    @ManyToOne
    @JoinColumn(name = "creator_id")
    @JsonIgnoreProperties({"password", "posts", "comments", "likes", "followers", "following"})
    private User creator;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (inviteCode == null || inviteCode.isBlank()) {
            inviteCode = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        }
    }
}
