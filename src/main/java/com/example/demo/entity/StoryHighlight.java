package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "story_highlights",
       indexes = @Index(name = "idx_highlights_user", columnList = "userId, createdAt"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StoryHighlight {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false, length = 60)
    private String title;

    @Column(length = 512)
    private String coverUrl;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
