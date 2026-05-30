package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "story_reactions",
       uniqueConstraints = @UniqueConstraint(name = "uq_story_react",
               columnNames = {"storyId", "userId", "emoji"}),
       indexes = @Index(name = "idx_story_react_story", columnList = "storyId"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StoryReaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private Long storyId;
    @Column(nullable = false) private Long userId;

    @Column(nullable = false, length = 8)
    private String emoji;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
