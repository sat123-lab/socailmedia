package com.example.demo.dto;

import com.example.demo.entity.WebRtcSignal;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class WebRtcSignalDto {
    private Long id;
    private Long fromUserId;
    private Long toUserId;
    private String signalType;
    private String payload;
    private LocalDateTime createdAt;

    public static WebRtcSignalDto from(WebRtcSignal s) {
        return WebRtcSignalDto.builder()
                .id(s.getId())
                .fromUserId(s.getFromUserId())
                .toUserId(s.getToUserId())
                .signalType(s.getSignalType())
                .payload(s.getPayload())
                .createdAt(s.getCreatedAt())
                .build();
    }
}
