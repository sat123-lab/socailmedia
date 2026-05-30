package com.example.demo.dto;

import com.example.demo.entity.LiveStreamMessage;
import com.example.demo.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LiveMessageDto {
    private Long id;
    private Long streamId;
    private Long senderId;
    private String senderName;
    private String senderAvatar;
    private boolean senderVerified;
    private String body;
    private String kind;
    private boolean pinned;
    private Instant createdAt;

    public static LiveMessageDto of(LiveStreamMessage m) {
        User s = m.getSender();
        return LiveMessageDto.builder()
                .id(m.getId())
                .streamId(m.getStream() == null ? null : m.getStream().getId())
                .senderId(s == null ? null : s.getId())
                .senderName(s == null ? null : s.getName())
                .senderAvatar(s == null ? null : s.getProfileImage())
                .senderVerified(s != null && s.isVerified())
                .body(m.getBody())
                .kind(m.getKind())
                .pinned(Boolean.TRUE.equals(m.getPinned()))
                .createdAt(m.getCreatedAt())
                .build();
    }
}
