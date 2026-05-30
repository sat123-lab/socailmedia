package com.example.demo.dto;

import com.example.demo.entity.RoomCallSession;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class RoomCallSessionDto {
    private Long id;
    private Long roomId;
    private String roomName;
    private String roomEmoji;
    private Long starterId;
    private String starterName;
    private String callType;
    private String status;
    private String myStatus;
    private boolean starter;
    private boolean hasPendingJoinRequest;
    private int pendingJoinRequestCount;
    private LocalDateTime createdAt;
    private List<Long> joinedUserIds;

    public static RoomCallSessionDto from(
            RoomCallSession session,
            String myStatus,
            List<Long> joinedUserIds,
            boolean isStarter,
            boolean hasPendingJoinRequest,
            int pendingJoinRequestCount
    ) {
        return RoomCallSessionDto.builder()
                .id(session.getId())
                .roomId(session.getRoom().getId())
                .roomName(session.getRoom().getName())
                .roomEmoji(session.getRoom().getEmoji())
                .starterId(session.getStarter().getId())
                .starterName(session.getStarter().getName())
                .callType(session.getCallType())
                .status(session.getStatus())
                .myStatus(myStatus)
                .starter(isStarter)
                .hasPendingJoinRequest(hasPendingJoinRequest)
                .pendingJoinRequestCount(pendingJoinRequestCount)
                .createdAt(session.getCreatedAt())
                .joinedUserIds(joinedUserIds)
                .build();
    }
}
