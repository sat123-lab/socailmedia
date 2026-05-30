package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class GroupDetailDto {
    private Long id;
    private String name;
    private Long creatorId;
    private boolean iAmAdmin;
    private List<GroupMemberDto> members;
}
