package com.example.demo.dto;

import com.example.demo.entity.RoomCallJoinRequest;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class RoomCallJoinRequestDto {
    private Long id;
    private Long userId;
    private String userName;
    private String status;
    private LocalDateTime createdAt;

    public static RoomCallJoinRequestDto from(RoomCallJoinRequest r) {
        return RoomCallJoinRequestDto.builder()
                .id(r.getId())
                .userId(r.getUser().getId())
                .userName(r.getUser().getName())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
