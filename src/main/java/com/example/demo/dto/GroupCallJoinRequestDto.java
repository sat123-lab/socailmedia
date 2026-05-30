package com.example.demo.dto;

import com.example.demo.entity.GroupCallJoinRequest;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class GroupCallJoinRequestDto {
    private Long id;
    private Long userId;
    private String userName;
    private String status;
    private LocalDateTime createdAt;

    public static GroupCallJoinRequestDto from(GroupCallJoinRequest r) {
        return GroupCallJoinRequestDto.builder()
                .id(r.getId())
                .userId(r.getUser().getId())
                .userName(r.getUser().getName())
                .status(r.getStatus())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
