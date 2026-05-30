package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KeyBundleUploadRequest {
    private String identityPublicKey;
    private String signingPublicKey;
    private String algorithm;
    private Integer keyVersion;
    private List<OneTimePreKeyDto> oneTimePreKeys;
}
