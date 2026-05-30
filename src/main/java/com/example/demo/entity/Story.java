package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "stories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({
            "password",
            "posts",
            "comments",
            "likes",
            "followers",
            "following"
    })
    private User user;

    private String mediaUrl;

    private String mediaType;

    // -------- Stories 2.0 (Phase 7) ----------------------------------
    /** Optional background music — short clip URL. */
    @Column(length = 512)
    private String musicUrl;

    @Column(length = 200)
    private String musicTitle;

    /** Only the creator's close-friends list can view this story. */
    @Builder.Default
    @Column(nullable = false)
    private boolean closeFriendsOnly = false;

    /**
     * JSON-encoded sticker overlays (text, mentions, hashtags, locations).
     * Storing as opaque JSON keeps schema migrations cheap as the sticker
     * vocabulary grows.
     */
    @Column(columnDefinition = "TEXT")
    private String stickerJson;

    @Builder.Default
    @Column(nullable = false)
    private int reactionCount = 0;
    // -----------------------------------------------------------------

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (expiresAt == null) {
            expiresAt = createdAt.plusHours(24);
        }
    }
}
