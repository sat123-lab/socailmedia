package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Returned by any auth endpoint that successfully establishes a session. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthTokensDto {
    private String accessToken;
    private String refreshToken;
    private long accessExpiresIn;
    private long refreshExpiresIn;
    private Long userId;
    private String email;
    private String name;
    private boolean admin;
}
