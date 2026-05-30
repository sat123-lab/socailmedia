package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AdminStatsDto {
    private long users;
    private long posts;
    private long comments;
    private long likes;
    private long follows;
    private long notifications;
    private long messages;
    private long stories;
    private long chatRooms;
    private long activeChatRooms;
    private long savedPosts;
    private long blockedAccounts;
}
