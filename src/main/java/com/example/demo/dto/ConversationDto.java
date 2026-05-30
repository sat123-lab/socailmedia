package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDto {

    private Long conversationId;

    private Long otherUserId;

    private String otherUserName;

    private String lastMessage;

    private Long sharedPostId;

    private LocalDateTime lastMessageAt;

    private boolean online;

    private String lastSeenText;
}
