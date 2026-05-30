package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The full public-key bundle for a single user — everything a sender needs to start an
 * end-to-end encrypted conversation. No private material is ever included.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyBundleDto {
    private Long userId;
    private int keyVersion;
    private String identityPublicKey;     // X25519, base64
    private String signingPublicKey;      // Ed25519, base64
    private String algorithm;             // e.g. "X25519-Ed25519-AESGCM"
    /** One-time pre-key (already consumed when this DTO was built). */
    private OneTimePreKeyDto oneTimePreKey;
}
