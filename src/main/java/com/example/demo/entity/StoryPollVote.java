package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "story_poll_votes",
       uniqueConstraints = @UniqueConstraint(name = "uq_poll_user", columnNames = {"pollId", "userId"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StoryPollVote {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private Long pollId;
    @Column(nullable = false) private Long userId;

    /** {@code 'A'} or {@code 'B'} — we keep it as a single CHAR for compactness. */
    @Column(nullable = false, length = 1)
    private String choice;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
