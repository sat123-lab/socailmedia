package com.example.demo.dto;

import com.example.demo.entity.CallSession;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CallSessionDto {
    private Long id;
    private Long callerId;
    private String callerName;
    private Long receiverId;
    private String receiverName;
    private String callType;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime answeredAt;
    private LocalDateTime endedAt;

    public static CallSessionDto from(CallSession session) {
        return CallSessionDto.builder()
                .id(session.getId())
                .callerId(session.getCaller().getId())
                .callerName(session.getCaller().getName())
                .receiverId(session.getReceiver().getId())
                .receiverName(session.getReceiver().getName())
                .callType(session.getCallType())
                .status(session.getStatus())
                .createdAt(session.getCreatedAt())
                .answeredAt(session.getAnsweredAt())
                .endedAt(session.getEndedAt())
                .build();
    }
}
