package com.example.demo.dto;

import com.example.demo.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Creator card for Explore + Creator Discovery surfaces. Carries
 * everything the UI paints without forcing a follow-up profile fetch.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExploreCreatorDto {

    private Long id;
    private String name;
    private String profileImage;
    private String bio;
    private boolean verified;
    private String accountType;
    private String category;
    private int followersCount;

    public static ExploreCreatorDto from(User u) {
        return ExploreCreatorDto.builder()
                .id(u.getId())
                .name(u.getName())
                .profileImage(u.getProfileImage())
                .bio(u.getBio())
                .verified(u.isVerified())
                .accountType(u.getAccountType())
                .category(u.getCategory())
                .followersCount(u.getFollowersCount())
                .build();
    }
}
