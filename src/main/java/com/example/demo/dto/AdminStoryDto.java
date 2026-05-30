package com.example.demo.dto;

import com.example.demo.entity.Story;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminStoryDto {
    private Long id;
    private Long userId;
    private String username;
    private String profileImage;
    private String mediaUrl;
    private String mediaType;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private boolean active;

    public static AdminStoryDto from(Story story) {
        boolean active = story.getExpiresAt() == null
                || story.getExpiresAt().isAfter(LocalDateTime.now());
        return AdminStoryDto.builder()
                .id(story.getId())
                .userId(story.getUser() != null ? story.getUser().getId() : null)
                .username(story.getUser() != null ? story.getUser().getName() : "Unknown")
                .profileImage(story.getUser() != null ? story.getUser().getProfileImage() : null)
                .mediaUrl(story.getMediaUrl())
                .mediaType(story.getMediaType())
                .createdAt(story.getCreatedAt())
                .expiresAt(story.getExpiresAt())
                .active(active)
                .build();
    }
}
