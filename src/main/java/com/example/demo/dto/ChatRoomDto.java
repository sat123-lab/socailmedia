package com.example.demo.dto;

import com.example.demo.entity.ChatRoom;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ChatRoomDto {
    private Long id;
    private String name;
    private String emoji;
    private Long creatorId;
    private String creatorName;
    private int memberCount;
    private LocalDateTime createdAt;
    private List<Long> memberIds;
    private String inviteCode;
    private String inviteLink;
    private boolean active;

    public static ChatRoomDto from(ChatRoom room, int memberCount, List<Long> memberIds) {
        String code = room.getInviteCode();
        String link = code != null ? "connect://join/" + code : null;
        return ChatRoomDto.builder()
                .id(room.getId())
                .name(room.getName())
                .emoji(room.getEmoji())
                .creatorId(room.getCreator().getId())
                .creatorName(room.getCreator().getName())
                .memberCount(memberCount)
                .createdAt(room.getCreatedAt())
                .memberIds(memberIds)
                .inviteCode(code)
                .inviteLink(link)
                .active(room.isActive())
                .build();
    }
}
