package com.example.demo.dto;

import lombok.Data;

@Data
public class GoogleAuthRequest {
    private String email;
    private String name;
    private String googleId;
    private String profileImage;
    private String idToken;

    /** Optional — see {@link RegisterRequest#getReferralCode()}. */
    private String referralCode;
    /** Optional — see {@link RegisterRequest#getDeviceId()}. */
    private String deviceId;
}
