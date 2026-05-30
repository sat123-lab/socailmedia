package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "reel_likes",
       uniqueConstraints = @UniqueConstraint(name = "uq_reel_user", columnNames = {"reelId", "userId"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReelLike {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private Long reelId;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private LocalDateTime createdAt;

    @PrePersist void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
