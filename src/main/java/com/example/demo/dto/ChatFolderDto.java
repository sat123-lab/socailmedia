package com.example.demo.dto;

import com.example.demo.entity.ChatFolder;

public class ChatFolderDto {
    public Long id;
    public String name;
    public String emoji;
    public String kind;
    public int sortOrder;
    public int chatCount;

    public static ChatFolderDto from(ChatFolder f, int chatCount) {
        ChatFolderDto d = new ChatFolderDto();
        d.id = f.getId();
        d.name = f.getName();
        d.emoji = f.getEmoji();
        d.kind = f.getKind();
        d.sortOrder = f.getSortOrder();
        d.chatCount = chatCount;
        return d;
    }
}
