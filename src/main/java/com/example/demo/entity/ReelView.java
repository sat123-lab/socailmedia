package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reel_views", indexes = {
        @Index(name = "idx_reel_views_reel", columnList = "reelId"),
        @Index(name = "idx_reel_views_user", columnList = "userId, createdAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReelView {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long reelId;

    private Long userId;

    @Column(nullable = false)
    private int watchMs;

    @Builder.Default
    @Column(nullable = false)
    private boolean completed = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
