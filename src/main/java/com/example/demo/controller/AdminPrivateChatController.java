package com.example.demo.controller;

import com.example.demo.dto.AdminPrivateChatDto;
import com.example.demo.service.AdminPrivateChatService;
import com.example.demo.service.AdminPrivateChatService.PagedResult;
import com.example.demo.service.AdminPrivateChatService.SearchFilter;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

/**
 * REST surface for the admin "Private Chats Monitor" page.
 *
 * <p>{@code /admin/**} is already gated by:</p>
 * <ul>
 *   <li>{@code SecurityConfig.authorizeHttpRequests}: requires {@code ROLE_ADMIN}.</li>
 *   <li>{@code AdminAccessGuard}: optional IP allowlist + admin-session TTL.</li>
 *   <li>{@code AdminSecurityService.requireAdmin}: belt-and-braces check inside
 *       the service.</li>
 * </ul>
 *
 * <p>Combined with the {@link AdminPrivateChatDto} contract — which strips
 * every byte of message content before leaving the DTO factory — the panel can
 * monitor metadata without ever decrypting or even seeing the bodies.</p>
 */
@RestController
@RequestMapping("/admin/private-chats")
@RequiredArgsConstructor
public class AdminPrivateChatController {

    private final AdminPrivateChatService service;

    /**
     * Paginated + filtered listing.
     *
     * <pre>
     *   GET /admin/private-chats
     *       ?type=DIRECT|GROUP|ROOM            (optional, default = all)
     *       &senderId=42                       (optional)
     *       &from=2026-05-01T00:00:00          (optional, ISO-LOCAL)
     *       &to=2026-05-31T23:59:59            (optional, ISO-LOCAL)
     *       &encryptedOnly=true                (optional)
     *       &q=alice                           (optional fuzzy match on names)
     *       &page=0
     *       &size=50                           (clamped to 1..200)
     * </pre>
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public PagedResult list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long senderId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false, defaultValue = "false") boolean encryptedOnly,
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "50") int size,
            Authentication authentication,
            HttpServletRequest request) {

        SearchFilter f = SearchFilter.builder()
                .type(parseType(type))
                .senderId(senderId)
                .from(parseDate(from))
                .to(parseDate(to))
                .encryptedOnly(encryptedOnly)
                .query(q)
                .page(page)
                .size(size)
                .build();
        return service.search(authentication, request, f);
    }

    private static AdminPrivateChatDto.Type parseType(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("ALL")) return null;
        try {
            return AdminPrivateChatDto.Type.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static LocalDateTime parseDate(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return LocalDateTime.parse(raw);
        } catch (DateTimeParseException e) {
            // Accept date-only as start-of-day
            try {
                return LocalDateTime.parse(raw + "T00:00:00");
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }
}
