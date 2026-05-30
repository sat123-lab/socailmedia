package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "story_polls",
       indexes = @Index(name = "idx_story_polls_story", columnList = "storyId"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StoryPoll {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storyId;

    @Column(nullable = false, length = 280)
    private String question;

    @Column(nullable = false, length = 100) private String optionA;
    @Column(nullable = false, length = 100) private String optionB;

    @Builder.Default @Column(nullable = false) private int votesA = 0;
    @Builder.Default @Column(nullable = false) private int votesB = 0;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
