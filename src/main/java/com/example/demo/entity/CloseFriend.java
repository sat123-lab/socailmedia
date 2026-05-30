package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "close_friends",
       uniqueConstraints = @UniqueConstraint(name = "uq_close", columnNames = {"userId", "friendId"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CloseFriend {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private Long friendId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void prePersist() { if (createdAt == null) createdAt = LocalDateTime.now(); }
}
