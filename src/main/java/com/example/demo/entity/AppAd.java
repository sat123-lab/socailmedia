package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "app_ads")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AppAd {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 500)
    private String subtitle;

    @Column(nullable = false, length = 500)
    private String imageUrl;

    @Column(length = 500)
    private String targetUrl;

    /** FEED, REELS, STORY */
    @Column(nullable = false, length = 20)
    private String placement;

    @Column(nullable = false)
    private boolean active;

    private int sortOrder;

    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (placement == null) placement = "FEED";
    }
}
