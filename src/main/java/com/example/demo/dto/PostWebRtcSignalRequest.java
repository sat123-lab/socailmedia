package com.example.demo.dto;

import lombok.Data;

@Data
public class PostWebRtcSignalRequest {
    private String signalType;
    private String payload;
    private Long toUserId;
}
