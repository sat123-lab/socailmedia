package com.example.demo.dto;

import com.example.demo.entity.GroupCallSession;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class GroupCallSessionDto {
    private Long id;
    private Long groupId;
    private String groupName;
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

    public static GroupCallSessionDto from(
            GroupCallSession session,
            String myStatus,
            List<Long> joinedUserIds,
            boolean isStarter,
            boolean hasPendingJoinRequest,
            int pendingJoinRequestCount
    ) {
        return GroupCallSessionDto.builder()
                .id(session.getId())
                .groupId(session.getGroup().getId())
                .groupName(session.getGroup().getName())
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
