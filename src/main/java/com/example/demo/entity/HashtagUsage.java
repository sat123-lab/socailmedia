package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "hashtag_usage", indexes = {
        @Index(name = "idx_hashtag_usage_tag",    columnList = "tag, createdAt"),
        @Index(name = "idx_hashtag_usage_entity", columnList = "entityType, entityId")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class HashtagUsage {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String tag;

    @Column(nullable = false, length = 16)
    private String entityType;

    @Column(nullable = false)
    private Long entityId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
