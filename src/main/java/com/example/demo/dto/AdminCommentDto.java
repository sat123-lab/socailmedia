package com.example.demo.dto;

import com.example.demo.entity.Comment;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class AdminCommentDto {
    private Long id;
    private String text;
    private Long userId;
    private String username;
    private String userProfileImage;
    private Long postId;
    private String postCaption;
    private LocalDateTime createdAt;

    public static AdminCommentDto from(Comment comment) {
        String postCaption = null;
        Long postId = null;
        if (comment.getPost() != null) {
            postId = comment.getPost().getId();
            postCaption = comment.getPost().getCaption();
            if (postCaption != null && postCaption.length() > 60) {
                postCaption = postCaption.substring(0, 60) + "…";
            }
        }
        return AdminCommentDto.builder()
                .id(comment.getId())
                .text(comment.getText())
                .userId(comment.getUser() != null ? comment.getUser().getId() : null)
                .username(comment.getUser() != null ? comment.getUser().getName() : "Unknown")
                .userProfileImage(comment.getUser() != null ? comment.getUser().getProfileImage() : null)
                .postId(postId)
                .postCaption(postCaption)
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
