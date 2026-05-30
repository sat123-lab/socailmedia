package com.example.demo.dto;

import com.example.demo.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSummaryDto {

    private Long id;

    private String name;

    private String bio;

    private String profileImage;

    private boolean privateAccount;

    public static UserSummaryDto from(User user) {
        return UserSummaryDto.builder()
                .id(user.getId())
                .name(user.getName())
                .bio(user.getBio())
                .profileImage(user.getProfileImage())
                .privateAccount(user.isPrivateAccount())
                .build();
    }
}
