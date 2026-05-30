package com.example.demo.dto;

import com.example.demo.entity.ChatRoomMessage;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ChatRoomMessageDto {
    private Long id;
    private Long roomId;
    private Long senderId;
    private String senderName;
    private String senderProfileImage;
    private String content;
    private LocalDateTime createdAt;

    public static ChatRoomMessageDto from(ChatRoomMessage message) {
        return ChatRoomMessageDto.builder()
                .id(message.getId())
                .roomId(message.getRoom().getId())
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getName())
                .senderProfileImage(message.getSender().getProfileImage())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
