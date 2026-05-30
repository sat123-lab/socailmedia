package com.example.demo.service;

import com.example.demo.dto.AdminPrivateChatDto;
import com.example.demo.entity.User;
import com.example.demo.repository.ChatMessageRepository;
import com.example.demo.repository.ChatRoomMessageRepository;
import com.example.demo.repository.SocialGroupMessageRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.AdminSecurityService;
import com.example.demo.security.AuditLogService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Read-only, audit-logged, redaction-enforced backing service for the
 * admin "Private Chats Monitor" page.
 *
 * <p>The service pulls rows from three message tables — direct chats
 * ({@code chat_messages}), social-group chats ({@code social_group_messages}),
 * and chat-room messages ({@code chat_room_messages}) — and projects them
 * through {@link AdminPrivateChatDto}, which stores ONLY metadata + a
 * redacted placeholder.</p>
 *
 * <h3>Privacy guarantees</h3>
 * <ul>
 *   <li>Plaintext / ciphertext message bodies never leave the entity. The DTO
 *       sees only the content-length (so admins can spot anomalies) and
 *       {@link AdminSecurityService#REDACTED}.</li>
 *   <li>Every call writes an {@code ADMIN_PRIVATE_CHAT_VIEW} audit row tagged
 *       with the admin's id, IP, and user-agent.</li>
 *   <li>Caller must be authenticated AND carry {@code ROLE_ADMIN}, validated
 *       by both {@link AdminSecurityService#requireAdmin(Authentication)} and
 *       {@code SecurityConfig}/{@code AdminAccessGuard}.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminPrivateChatService {

    private final ChatMessageRepository directRepo;
    private final SocialGroupMessageRepository groupRepo;
    private final ChatRoomMessageRepository roomRepo;
    private final UserRepository userRepository;
    private final AdminSecurityService adminSecurity;
    private final AuditLogService auditLog;

    public PagedResult search(Authentication authentication,
                              HttpServletRequest request,
                              SearchFilter f) {
        adminSecurity.requireAdmin(authentication);

        int pageNum = Math.max(0, f.page);
        int pageSize = Math.min(Math.max(f.size, 1), 200);
        Pageable pageable = PageRequest.of(pageNum, pageSize);

        // Pull one page from each relevant source, then merge + slice. This
        // keeps memory bounded even when the three tables are huge.
        boolean wantsDirect = f.type == null || f.type == AdminPrivateChatDto.Type.DIRECT;
        boolean wantsGroup  = f.type == null || f.type == AdminPrivateChatDto.Type.GROUP;
        boolean wantsRoom   = f.type == null || f.type == AdminPrivateChatDto.Type.ROOM;

        long totalDirect = 0L, totalGroup = 0L, totalRoom = 0L;

        Stream<AdminPrivateChatDto> stream = Stream.empty();
        if (wantsDirect) {
            Page<?> p = directRepo.adminSearch(f.senderId, f.from, f.to,
                    f.encryptedOnly, pageable);
            totalDirect = p.getTotalElements();
            stream = Stream.concat(stream, p.getContent().stream()
                    .map(m -> AdminPrivateChatDto.from((com.example.demo.entity.ChatMessage) m)));
        }
        if (wantsGroup) {
            Page<?> p = groupRepo.adminSearch(f.senderId, f.from, f.to,
                    f.encryptedOnly, pageable);
            totalGroup = p.getTotalElements();
            stream = Stream.concat(stream, p.getContent().stream()
                    .map(m -> AdminPrivateChatDto.from((com.example.demo.entity.SocialGroupMessage) m)));
        }
        if (wantsRoom) {
            Page<?> p = roomRepo.adminSearch(f.senderId, f.from, f.to,
                    f.encryptedOnly, pageable);
            totalRoom = p.getTotalElements();
            stream = Stream.concat(stream, p.getContent().stream()
                    .map(m -> AdminPrivateChatDto.from((com.example.demo.entity.ChatRoomMessage) m)));
        }

        // Apply server-side text filter on sender / receiver names only — never
        // on content, which we have already redacted.
        final String q = f.query == null ? null : f.query.trim().toLowerCase(Locale.ROOT);
        List<AdminPrivateChatDto> merged = stream
                .filter(d -> q == null || q.isBlank() || matchesQuery(d, q))
                .sorted(Comparator.comparing(AdminPrivateChatDto::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        long mergedTotal = totalDirect + totalGroup + totalRoom;
        List<AdminPrivateChatDto> slice = merged.stream()
                .limit(pageSize)
                .toList();

        // ---- AUDIT LOG ---- written *after* we've gathered metadata so the
        // payload sizes can be included for forensic correlation.
        User actor = currentAdmin(authentication);
        Map<String, Object> meta = new HashMap<>();
        meta.put("page", pageNum);
        meta.put("size", pageSize);
        meta.put("type", f.type == null ? "ALL" : f.type.name());
        meta.put("senderId", f.senderId);
        meta.put("encryptedOnly", f.encryptedOnly);
        meta.put("query", f.query);
        meta.put("returned", slice.size());
        meta.put("totalDirect", totalDirect);
        meta.put("totalGroup", totalGroup);
        meta.put("totalRoom", totalRoom);
        auditLog.record("ADMIN_PRIVATE_CHAT_VIEW",
                actor == null ? null : actor.getId(),
                null, "PrivateChats",
                stringify(meta),
                request);

        return new PagedResult(slice, mergedTotal, pageNum, pageSize,
                totalDirect, totalGroup, totalRoom);
    }

    private static boolean matchesQuery(AdminPrivateChatDto d, String q) {
        return contains(d.getSenderName(), q) || contains(d.getReceiverName(), q);
    }

    private static boolean contains(String haystack, String needle) {
        return haystack != null && haystack.toLowerCase(Locale.ROOT).contains(needle);
    }

    private User currentAdmin(Authentication a) {
        if (a == null || a.getName() == null) return null;
        return userRepository.findByEmail(a.getName()).orElse(null);
    }

    private static String stringify(Map<String, Object> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> e : m.entrySet()) {
            if (sb.length() > 0) sb.append(';');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    // ===========================================================
    //  Value objects
    // ===========================================================

    @lombok.AllArgsConstructor
    @lombok.NoArgsConstructor
    @lombok.Data
    @lombok.Builder
    public static class SearchFilter {
        private AdminPrivateChatDto.Type type;
        private Long senderId;
        private LocalDateTime from;
        private LocalDateTime to;
        private boolean encryptedOnly;
        private String query;
        @lombok.Builder.Default
        private int page = 0;
        @lombok.Builder.Default
        private int size = 50;
    }

    public record PagedResult(List<AdminPrivateChatDto> items,
                              long total,
                              int page,
                              int size,
                              long totalDirect,
                              long totalGroup,
                              long totalRoom) { }
}
