package com.example.demo.dto;

import com.example.demo.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoryGroupDto {

    private Long userId;

    private String userName;

    private String profileImage;

    private boolean isOwn;

    private boolean hasUnseen;

    private List<StoryItemDto> stories;

    /**
     * Lightweight builder for surfaces (e.g. the Explore Hub's
     * "Popular Stories" rail) that only need the creator chrome — the
     * actual story items get fetched on tap when the user enters the
     * full-screen viewer.
     */
    public static StoryGroupDto headerOnly(User u) {
        return StoryGroupDto.builder()
                .userId(u.getId())
                .userName(u.getName())
                .profileImage(u.getProfileImage())
                .isOwn(false)
                .hasUnseen(true)
                .stories(java.util.List.of())
                .build();
    }
}
