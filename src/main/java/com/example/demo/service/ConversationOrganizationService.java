package com.example.demo.service;

import com.example.demo.dto.ChatFolderDto;
import com.example.demo.dto.ConversationSettingsDto;
import com.example.demo.entity.ChatFolder;
import com.example.demo.entity.Conversation;
import com.example.demo.entity.ConversationSettings;
import com.example.demo.entity.User;
import com.example.demo.repository.ChatFolderRepository;
import com.example.demo.repository.ConversationRepository;
import com.example.demo.repository.ConversationSettingsRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Pin / archive / mute / folders — per-user organisation of conversations.
 */
@Service
@RequiredArgsConstructor
public class ConversationOrganizationService {

    public static final int MAX_FOLDERS = 20;

    private final ConversationSettingsRepository settings;
    private final ChatFolderRepository folders;
    private final ConversationRepository conversations;
    private final UserRepository users;
    private final RealtimeEventService realtime;

    // ============================================================
    //  SETTINGS
    // ============================================================

    public ConversationSettingsDto getSettings(Long userId, Long conversationId) {
        return ConversationSettingsDto.from(
                settings.findByUserIdAndConversationId(userId, conversationId)
                        .orElse(null));
    }

    @Transactional
    public ConversationSettingsDto pin(Long userId, Long conversationId) {
        requireParticipant(userId, conversationId);
        long pinned = settings.countByUserIdAndPinnedTrue(userId);
        if (pinned >= AdvancedMessagingService.MAX_PINS) {
            throw new IllegalStateException("Pin limit reached");
        }
        ConversationSettings row = getOrCreate(userId, conversationId);
        row.setPinned(true);
        row.setPinOrder((int) pinned);
        row.setUpdatedAt(Instant.now());
        settings.save(row);
        push(userId, RealtimeEventService.TYPE_CONV_PINNED, conversationId, true);
        return ConversationSettingsDto.from(row);
    }

    @Transactional
    public ConversationSettingsDto unpin(Long userId, Long conversationId) {
        ConversationSettings row = getOrCreate(userId, conversationId);
        row.setPinned(false);
        row.setPinOrder(0);
        row.setUpdatedAt(Instant.now());
        settings.save(row);
        push(userId, RealtimeEventService.TYPE_CONV_PINNED, conversationId, false);
        return ConversationSettingsDto.from(row);
    }

    @Transactional
    public ConversationSettingsDto archive(Long userId, Long conversationId) {
        requireParticipant(userId, conversationId);
        ConversationSettings row = getOrCreate(userId, conversationId);
        row.setArchived(true);
        row.setPinned(false);
        row.setUpdatedAt(Instant.now());
        settings.save(row);
        push(userId, RealtimeEventService.TYPE_CONV_ARCHIVED, conversationId, true);
        return ConversationSettingsDto.from(row);
    }

    @Transactional
    public ConversationSettingsDto unarchive(Long userId, Long conversationId) {
        ConversationSettings row = getOrCreate(userId, conversationId);
        row.setArchived(false);
        row.setUpdatedAt(Instant.now());
        settings.save(row);
        push(userId, RealtimeEventService.TYPE_CONV_ARCHIVED, conversationId, false);
        return ConversationSettingsDto.from(row);
    }

    @Transactional
    public ConversationSettingsDto mute(Long userId, Long conversationId, Instant until) {
        ConversationSettings row = getOrCreate(userId, conversationId);
        row.setMutedUntil(until);
        row.setUpdatedAt(Instant.now());
        settings.save(row);
        return ConversationSettingsDto.from(row);
    }

    @Transactional
    public void reorderPins(Long userId, List<Long> orderedConversationIds) {
        if (orderedConversationIds == null) return;
        int order = 0;
        for (Long convId : orderedConversationIds) {
            final int pinOrder = order++;
            settings.findByUserIdAndConversationId(userId, convId).ifPresent(row -> {
                if (row.isPinned()) {
                    row.setPinOrder(pinOrder);
                    row.setUpdatedAt(Instant.now());
                    settings.save(row);
                }
            });
        }
    }

    public List<ConversationSettingsDto> pinned(Long userId) {
        return settings.findPinned(userId).stream()
                .map(ConversationSettingsDto::from)
                .toList();
    }

    public List<ConversationSettingsDto> archived(Long userId) {
        return settings.findArchived(userId).stream()
                .map(ConversationSettingsDto::from)
                .toList();
    }

    // ============================================================
    //  FOLDERS
    // ============================================================

    public List<ChatFolderDto> listFolders(Long userId) {
        return folders.findByUserIdOrderBySortOrderAscIdAsc(userId).stream()
                .map(f -> ChatFolderDto.from(f,
                        settings.findInFolder(userId, f.getId()).size()))
                .toList();
    }

    @Transactional
    public ChatFolderDto createFolder(Long userId, String name, String emoji) {
        if (folders.countByUserId(userId) >= MAX_FOLDERS) {
            throw new IllegalStateException("Folder limit reached");
        }
        ChatFolder f = folders.save(ChatFolder.builder()
                .userId(userId)
                .name(name.trim())
                .emoji(emoji)
                .sortOrder((int) folders.countByUserId(userId))
                .build());
        return ChatFolderDto.from(f, 0);
    }

    @Transactional
    public void deleteFolder(Long userId, Long folderId) {
        ChatFolder f = folders.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new RuntimeException("Folder not found"));
        settings.detachFolder(userId, f.getId());
        folders.delete(f);
    }

    @Transactional
    public ConversationSettingsDto assignFolder(Long userId, Long conversationId, Long folderId) {
        if (folderId != null) {
            folders.findByIdAndUserId(folderId, userId)
                    .orElseThrow(() -> new RuntimeException("Folder not found"));
        }
        ConversationSettings row = getOrCreate(userId, conversationId);
        row.setFolderId(folderId);
        row.setUpdatedAt(Instant.now());
        settings.save(row);
        Map<String, Object> payload = new HashMap<>();
        payload.put("conversationId", conversationId);
        payload.put("folderId", folderId);
        realtime.toUser(userId, RealtimeEventService.TYPE_CONV_FOLDERED, payload);
        return ConversationSettingsDto.from(row);
    }

    // ============================================================
    //  helpers
    // ============================================================

    private ConversationSettings getOrCreate(Long userId, Long conversationId) {
        return settings.findByUserIdAndConversationId(userId, conversationId)
                .orElseGet(() -> settings.save(ConversationSettings.builder()
                        .userId(userId)
                        .conversationId(conversationId)
                        .build()));
    }

    private void requireParticipant(Long userId, Long conversationId) {
        Conversation c = conversations.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        boolean ok = (c.getUserOne() != null && c.getUserOne().getId().equals(userId))
                || (c.getUserTwo() != null && c.getUserTwo().getId().equals(userId));
        if (!ok) throw new SecurityException("Not a participant");
    }

    private void push(Long userId, String type, Long conversationId, boolean value) {
        realtime.toUser(userId, type, Map.of(
                "conversationId", conversationId,
                "value", value));
    }

    @SuppressWarnings("unused")
    private User requireUser(Long id) {
        return users.findById(id).orElseThrow(() -> new RuntimeException("User"));
    }
}
