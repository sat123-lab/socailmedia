package com.example.demo.dto;

import com.example.demo.entity.Story;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryItemDto {

    private Long id;

    private String mediaUrl;

    private String mediaType;

    private LocalDateTime createdAt;

    private LocalDateTime expiresAt;

    public static StoryItemDto from(Story story) {
        return StoryItemDto.builder()
                .id(story.getId())
                .mediaUrl(story.getMediaUrl())
                .mediaType(story.getMediaType())
                .createdAt(story.getCreatedAt())
                .expiresAt(story.getExpiresAt())
                .build();
    }
}
