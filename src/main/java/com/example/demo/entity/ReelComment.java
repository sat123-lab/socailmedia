package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reel_comments", indexes = {
        @Index(name = "idx_reel_comments_reel", columnList = "reelId, createdAt")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReelComment {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private Long reelId;
    @Column(nullable = false) private Long userId;

    @Column(nullable = false, length = 500)
    private String text;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
