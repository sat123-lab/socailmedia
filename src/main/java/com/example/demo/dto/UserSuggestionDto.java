package com.example.demo.dto;

import com.example.demo.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lightweight user shape used by the mention autocomplete picker.
 * Strips everything the suggestion row doesn't render — keeps the
 * payload tiny and avoids leaking sensitive fields (email, phone, etc.)
 * through a public-ish endpoint.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSuggestionDto {
    private Long id;
    private String name;
    private String profileImage;
    private boolean verified;
    private String accountType;

    public static UserSuggestionDto from(User u) {
        return UserSuggestionDto.builder()
                .id(u.getId())
                .name(u.getName())
                .profileImage(u.getProfileImage())
                .verified(u.isVerified())
                .accountType(u.getAccountType())
                .build();
    }
}
