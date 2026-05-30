package com.example.demo.security;

import com.example.demo.entity.ChatMessage;
import com.example.demo.entity.ChatRoomMessage;
import com.example.demo.entity.SocialGroupMessage;
import com.example.demo.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Hard boundary between admins and end-user content.
 *
 * <p>The admin panel can <em>moderate</em>, but it cannot <em>read</em> private
 * conversations. This service is the central place where that rule is enforced:</p>
 *
 * <ul>
 *   <li>{@link #redact(ChatMessage)} — replaces the message body with a placeholder.</li>
 *   <li>{@link #requireAdmin(Authentication)} — single source of truth for admin checks.</li>
 *   <li>{@link #logAdminAction} — every privileged action is audited.</li>
 * </ul>
 *
 * <h3>What admins see</h3>
 * <pre>
 * {
 *   "id":         123,
 *   "senderId":   8,
 *   "recipientId":12,
 *   "createdAt":  "2026-...",
 *   "encrypted":  true,
 *   "preview":    "[encrypted · 247 bytes]"
 * }
 * </pre>
 * Never the plaintext — even for non-E2EE messages, because no admin endpoint exists that
 * exposes decryption.
 */
@Service
@RequiredArgsConstructor
public class AdminSecurityService {

    public static final String REDACTED = "[encrypted content — not visible to admins]";

    private final AuditLogService auditLogService;

    public Map<String, Object> redact(ChatMessage m) {
        return Map.of(
                "id", m.getId(),
                "senderId", m.getSender() == null ? null : m.getSender().getId(),
                "conversationId", m.getConversation() == null ? null : m.getConversation().getId(),
                "createdAt", m.getCreatedAt(),
                "messageType", m.getMessageType(),
                "preview", preview(m.getContent())
        );
    }

    public Map<String, Object> redact(SocialGroupMessage m) {
        return Map.of(
                "id", m.getId(),
                "senderId", m.getSender() == null ? null : m.getSender().getId(),
                "groupId", m.getGroup() == null ? null : m.getGroup().getId(),
                "createdAt", m.getCreatedAt(),
                "preview", preview(m.getContent())
        );
    }

    public Map<String, Object> redact(ChatRoomMessage m) {
        return Map.of(
                "id", m.getId(),
                "senderId", m.getSender() == null ? null : m.getSender().getId(),
                "roomId", m.getRoom() == null ? null : m.getRoom().getId(),
                "createdAt", m.getCreatedAt(),
                "preview", preview(m.getContent())
        );
    }

    public boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(g -> "ROLE_ADMIN".equals(g.getAuthority()));
    }

    public void requireAdmin(Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new SecurityException("Admin access required");
        }
    }

    public void logAdminAction(User actor, String detail, Long targetId, String targetType,
                               HttpServletRequest request) {
        auditLogService.record(
                AuditLogService.ADMIN_ACTION,
                actor == null ? null : actor.getId(),
                targetId, targetType, detail, request);
    }

    private static String preview(String content) {
        if (content == null || content.isEmpty()) return REDACTED + " · empty";
        // Heuristic: looks like base64? → server-side encrypted, just hide. Either way, redact.
        return REDACTED + " · " + content.length() + " bytes";
    }
}
