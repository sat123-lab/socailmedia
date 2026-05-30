package com.example.demo.controller;

import com.example.demo.dto.CreatorStatsDto;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.CreatorService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Creator / Business account endpoints.
 *
 * <ul>
 *   <li>{@code GET  /creator/stats}        — caller's own dashboard.</li>
 *   <li>{@code GET  /creator/stats/{id}}   — public summary (for profile views).</li>
 *   <li>{@code PATCH /creator/account}     — switch to PERSONAL/CREATOR/BUSINESS.</li>
 * </ul>
 */
@RestController
@RequestMapping("/creator")
@RequiredArgsConstructor
public class CreatorController {

    private final CreatorService creatorService;
    private final UserRepository userRepository;

    @GetMapping("/stats")
    public CreatorStatsDto myStats(Authentication auth) {
        Long uid = currentUserId(auth);
        if (uid == null) throw new SecurityException("Not authenticated");
        return creatorService.stats(uid);
    }

    @GetMapping("/stats/{userId}")
    public CreatorStatsDto userStats(@PathVariable Long userId) {
        return creatorService.stats(userId);
    }

    @PatchMapping("/account")
    public User updateAccount(@RequestBody Map<String, String> body, Authentication auth) {
        Long uid = currentUserId(auth);
        if (uid == null) throw new SecurityException("Not authenticated");
        return creatorService.updateAccountType(uid,
                body.get("accountType"),
                body.get("bio"),
                body.get("website"),
                body.get("category"));
    }

    private Long currentUserId(Authentication a) {
        if (a == null) return null;
        return userRepository.findByEmail(a.getName()).map(User::getId).orElse(null);
    }
}
