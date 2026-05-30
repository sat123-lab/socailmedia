package com.example.demo.dto;

import lombok.Data;

/**
 * Sent by the Flutter client after Firebase Auth (Google or Phone)
 * returns an ID token on-device. The backend verifies the token using
 * Firebase Admin SDK and creates / loads the matching user.
 */
@Data
public class FirebaseAuthRequest {
    private String idToken;
    private String name;

    /** Optional — see {@link RegisterRequest#getReferralCode()}. */
    private String referralCode;

    /** Optional — see {@link RegisterRequest#getDeviceId()}. */
    private String deviceId;
}
