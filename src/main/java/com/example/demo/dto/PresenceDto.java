package com.example.demo.dto;

import com.example.demo.entity.UserPresence;

import java.time.Instant;

public class PresenceDto {
    public Long userId;
    public boolean online;
    public Instant lastSeenAt;
    public Long typingInConvId;
    public String typingKind;
    public boolean typing;

    public static PresenceDto from(UserPresence p) {
        PresenceDto d = new PresenceDto();
        if (p == null) {
            d.online = false;
            return d;
        }
        d.userId = p.getUserId();
        d.online = p.isOnline();
        d.lastSeenAt = p.getLastSeenAt();
        d.typingInConvId = p.getTypingInConvId();
        d.typingKind = p.getTypingKind();
        Instant until = p.getTypingUntil();
        d.typing = until != null && until.isAfter(Instant.now());
        return d;
    }
}
