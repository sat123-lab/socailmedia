package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class GroupMessageDto {
    private Long id;
    private String content;
    private Long senderId;
    private String senderName;
    private LocalDateTime createdAt;
}
