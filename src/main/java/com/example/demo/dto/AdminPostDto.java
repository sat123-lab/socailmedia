package com.example.demo.dto;

import com.example.demo.entity.Post;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminPostDto {
    private Long id;
    private String caption;
    private String imageUrl;
    private String videoUrl;
    private String type;
    private int likesCount;
    private int commentsCount;
    private Long userId;
    private String username;
    private LocalDateTime createdAt;

    public static AdminPostDto from(Post post) {
        return AdminPostDto.builder()
                .id(post.getId())
                .caption(post.getCaption())
                .imageUrl(post.getImageUrl())
                .videoUrl(post.getVideoUrl())
                .type(post.getType())
                .likesCount(post.getLikesCount())
                .commentsCount(post.getCommentsCount())
                .userId(post.getUser() != null ? post.getUser().getId() : null)
                .username(post.getUser() != null ? post.getUser().getName() : "Unknown")
                .createdAt(post.getCreatedAt())
                .build();
    }
}
