package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "group_call_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GroupCallSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "group_id")
    @JsonIgnoreProperties({"password", "posts", "comments", "likes", "followers", "following"})
    private SocialGroup group;

    @ManyToOne
    @JoinColumn(name = "starter_id")
    @JsonIgnoreProperties({"password", "posts", "comments", "likes", "followers", "following"})
    private User starter;

    /** VOICE or VIDEO */
    private String callType;

    /** RINGING, ACTIVE, ENDED */
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime endedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "RINGING";
        }
    }
}
