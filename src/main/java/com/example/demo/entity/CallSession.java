package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "call_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CallSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "caller_id")
    @JsonIgnoreProperties({"password", "posts", "comments", "likes", "followers", "following"})
    private User caller;

    @ManyToOne
    @JoinColumn(name = "receiver_id")
    @JsonIgnoreProperties({"password", "posts", "comments", "likes", "followers", "following"})
    private User receiver;

    // VOICE or VIDEO
    private String callType;

    // RINGING, ACTIVE, DECLINED, ENDED, BUSY
    private String status;

    private LocalDateTime createdAt;
    private LocalDateTime answeredAt;
    private LocalDateTime endedAt;

    @PrePersist
    public void prePersist() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = "RINGING";
        }
    }
}
