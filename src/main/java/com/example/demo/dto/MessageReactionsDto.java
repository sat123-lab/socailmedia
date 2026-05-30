package com.example.demo.dto;

import com.example.demo.entity.MessageReaction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Compact aggregate of reactions on a single message — emoji → count
 * map, plus the ids of users who reacted (so the client can render
 * "you, alice and 12 others reacted with 🔥" without an extra round-trip).
 */
public class MessageReactionsDto {

    public long messageId;
    public Map<String, Integer> counts;
    /** Reactions left specifically by the calling viewer, for the
     *  "I reacted with…" UI state. */
    public List<String> myReactions;

    public static MessageReactionsDto build(long messageId,
                                            List<MessageReaction> rows,
                                            Long viewerId) {
        MessageReactionsDto dto = new MessageReactionsDto();
        dto.messageId = messageId;
        dto.counts = new LinkedHashMap<>();
        dto.myReactions = new java.util.ArrayList<>();
        for (MessageReaction r : rows) {
            dto.counts.merge(r.getEmoji(), 1, Integer::sum);
            if (viewerId != null && viewerId.equals(r.getUserId())) {
                dto.myReactions.add(r.getEmoji());
            }
        }
        return dto;
    }
}
