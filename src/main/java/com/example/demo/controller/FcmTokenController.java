package com.example.demo.controller;

import com.example.demo.entity.FcmToken;
import com.example.demo.entity.User;
import com.example.demo.repository.FcmTokenRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Endpoints used by the Flutter client to keep its FCM registration in sync
 * with the server.
 *
 * <ul>
 *   <li>{@code POST /fcm/register}     — first install + on token rotation</li>
 *   <li>{@code DELETE /fcm/unregister} — on logout</li>
 *   <li>{@code GET /fcm/mine}          — debug: list this user's live tokens</li>
 * </ul>
 */
@RestController
@RequestMapping("/fcm")
@RequiredArgsConstructor
public class FcmTokenController {

    private final FcmTokenRepository tokens;
    private final UserRepository users;

    @PostMapping("/register")
    public Map<String, Object> register(@RequestBody RegisterRequest req, Authentication auth) {
        Long uid = userId(auth);
        if (uid == null) throw new SecurityException("Not authenticated");
        if (req.token == null || req.token.isBlank()) throw new IllegalArgumentException("token required");

        FcmToken row = tokens.findByToken(req.token).orElseGet(() ->
                tokens.save(FcmToken.builder()
                        .userId(uid).token(req.token).build()));
        row.setUserId(uid); // re-bind if the same physical device just logged in as someone new
        row.setPlatform(req.platform);
        row.setDeviceName(req.deviceName);
        row.setLocale(req.locale);
        row.setAppVersion(req.appVersion);
        row.setInvalid(false);
        row.setLastSeenAt(LocalDateTime.now());
        tokens.save(row);
        return Map.of("status", "ok", "id", row.getId());
    }

    @DeleteMapping("/unregister")
    public Map<String, Object> unregister(@RequestParam String token) {
        tokens.markInvalid(token);
        return Map.of("status", "ok");
    }

    @GetMapping("/mine")
    public List<FcmToken> mine(Authentication auth) {
        Long uid = userId(auth);
        if (uid == null) return List.of();
        return tokens.findByUserIdAndInvalidFalse(uid);
    }

    private Long userId(Authentication auth) {
        if (auth == null) return null;
        return users.findByEmail(auth.getName()).map(User::getId).orElse(null);
    }

    public static class RegisterRequest {
        public String token;
        public String platform;
        public String deviceName;
        public String locale;
        public String appVersion;
    }
}
