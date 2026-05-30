package com.example.demo.dto;

import lombok.Data;

@Data
public class PhoneVerifyRequest {
    private String phone;
    private String otp;
    private String name;

    /** Optional — see {@link RegisterRequest#getReferralCode()}. */
    private String referralCode;
    /** Optional — see {@link RegisterRequest#getDeviceId()}. */
    private String deviceId;
}
