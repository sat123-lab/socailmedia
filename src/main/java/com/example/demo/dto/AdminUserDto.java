package com.example.demo.dto;

import com.example.demo.entity.User;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminUserDto {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private int followersCount;
    private int followingCount;
    private boolean admin;
    private boolean privateAccount;
    private String profileImage;

    public static AdminUserDto from(User user) {
        return AdminUserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .followersCount(user.getFollowersCount())
                .followingCount(user.getFollowingCount())
                .admin(user.isAdmin())
                .privateAccount(user.isPrivateAccount())
                .profileImage(user.getProfileImage())
                .build();
    }
}
