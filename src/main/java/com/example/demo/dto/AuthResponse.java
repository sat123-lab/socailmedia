package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Legacy auth payload — kept for backward compatibility.
 *
 * <p>Constructors are explicit (not Lombok {@code @AllArgsConstructor}) because we still
 * need the original {@code new AuthResponse(token, message)} call sites to compile after
 * we added new optional fields.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {

    private String token;

    private String message;

    /** Long-lived refresh token (HS512). Optional — pre-upgrade clients can ignore it. */
    private String refreshToken;

    /** Access-token TTL in seconds — useful for clients to schedule a proactive refresh. */
    private Long accessExpiresIn;

    /** Backwards-compatible 2-arg constructor preserved for legacy call sites. */
    public AuthResponse(String token, String message) {
        this.token = token;
        this.message = message;
    }
}
