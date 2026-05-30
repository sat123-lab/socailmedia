package com.example.demo.controller;

import com.example.demo.entity.ChatMessage;
import com.example.demo.service.MessageLifecycleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Read-receipt + delete-for-everyone endpoints.
 *
 * <ul>
 *   <li>{@code POST /messages/{id}/read} — marks a message as read.</li>
 *   <li>{@code DELETE /messages/{id}/everyone} — sender wipes the message
 *       on every device + the database.</li>
 * </ul>
 */
@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
public class MessageLifecycleController {

    private final MessageLifecycleService lifecycle;

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable Long id,
                                                        Authentication authentication) {
        ChatMessage m = lifecycle.markAsRead(id, authentication.getName());
        return ResponseEntity.ok(Map.of(
                "messageId", m.getId(),
                "readAt", String.valueOf(m.getReadAt()),
                "expiresAt", m.getExpiresAt() == null ? "" : m.getExpiresAt().toString()));
    }

    @DeleteMapping("/{id}/everyone")
    public ResponseEntity<Map<String, Object>> deleteForEveryone(@PathVariable Long id,
                                                                 Authentication authentication) {
        lifecycle.deleteForEveryone(id, authentication.getName());
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }
}
