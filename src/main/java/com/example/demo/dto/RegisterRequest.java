package com.example.demo.dto;

import lombok.Data;

@Data
public class RegisterRequest {

    private String name;

    private String email;

    private String phone;

    private String password;

    private String authProvider;

    /**
     * Optional — referral code that brought this signup in. When
     * present and valid, the new account is credited to its owner via
     * {@link com.example.demo.service.ReferralService}.
     */
    private String referralCode;

    /**
     * Optional client-supplied device fingerprint. Hashed before
     * storage in the referrals table; used for fraud heuristics.
     */
    private String deviceId;
}