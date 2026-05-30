package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "hashtags",
       uniqueConstraints = @UniqueConstraint(name = "uq_hashtag", columnNames = "tag"),
       indexes = @Index(name = "idx_hashtag_trending", columnList = "usageCount, lastUsedAt"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Hashtag {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String tag;

    @Builder.Default
    @Column(nullable = false)
    private long usageCount = 0;

    @Column(nullable = false)
    private LocalDateTime lastUsedAt;

    @PrePersist
    void prePersist() { if (lastUsedAt == null) lastUsedAt = LocalDateTime.now(); }
}
