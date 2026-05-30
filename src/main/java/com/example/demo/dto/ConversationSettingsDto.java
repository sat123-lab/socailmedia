package com.example.demo.dto;

import com.example.demo.entity.ConversationSettings;

import java.time.Instant;

public class ConversationSettingsDto {
    public Long conversationId;
    public boolean pinned;
    public int pinOrder;
    public boolean archived;
    public Instant mutedUntil;
    public Long folderId;
    public String notifications;

    public static ConversationSettingsDto from(ConversationSettings s) {
        if (s == null) return empty(null);
        ConversationSettingsDto d = new ConversationSettingsDto();
        d.conversationId = s.getConversationId();
        d.pinned         = s.isPinned();
        d.pinOrder       = s.getPinOrder();
        d.archived       = s.isArchived();
        d.mutedUntil     = s.getMutedUntil();
        d.folderId       = s.getFolderId();
        d.notifications  = s.getNotifications();
        return d;
    }

    public static ConversationSettingsDto empty(Long convId) {
        ConversationSettingsDto d = new ConversationSettingsDto();
        d.conversationId = convId;
        d.pinned         = false;
        d.pinOrder       = 0;
        d.archived       = false;
        d.mutedUntil     = null;
        d.folderId       = null;
        d.notifications  = "ALL";
        return d;
    }
}
