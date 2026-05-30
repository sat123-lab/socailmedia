package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // RECEIVER

    @ManyToOne
    @JoinColumn(name = "receiver_id")
    @JsonIgnore
    private User receiver;

    // SENDER

    @ManyToOne
    @JoinColumn(name = "sender_id")
    @JsonIgnoreProperties({
            "password",
            "posts",
            "comments",
            "likes",
            "followers",
            "following"
    })
    private User sender;

    // TYPE
    // LIKE
    // COMMENT
    // FOLLOW

    private String type;

    // MESSAGE

    private String message;

    // POST ID OPTIONAL

    private Long postId;

    // RELATED ID (e.g. follow request)

    private Long relatedId;

    // READ STATUS

    @Column(name = "is_read")
    @lombok.Builder.Default
    private boolean read = false;

    // CREATED TIME

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {

        createdAt = LocalDateTime.now();
    }
}