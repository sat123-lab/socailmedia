package com.example.demo.dto;

import com.example.demo.entity.User;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BlockedUserDto {
    private Long id;
    private String name;
    private String profileImage;

    public static BlockedUserDto from(User user) {
        return BlockedUserDto.builder()
                .id(user.getId())
                .name(user.getName())
                .profileImage(user.getProfileImage())
                .build();
    }
}
