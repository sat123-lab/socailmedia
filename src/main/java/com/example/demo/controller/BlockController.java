package com.example.demo.controller;

import com.example.demo.dto.BlockedUserDto;
import com.example.demo.service.BlockService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/blocks")
@RequiredArgsConstructor
@CrossOrigin("*")
public class BlockController {

    private final BlockService blockService;

    @GetMapping
    public List<BlockedUserDto> listBlocked(Authentication authentication) {
        return blockService.getBlockedUsers(authentication);
    }

    @GetMapping("/status/{userId}")
    public Map<String, Boolean> blockStatus(
            @PathVariable Long userId,
            Authentication authentication
    ) {
        return Map.of("blocked", blockService.isBlocked(userId, authentication));
    }

    @PostMapping("/{userId}")
    public String block(
            @PathVariable Long userId,
            Authentication authentication
    ) {
        return blockService.blockUser(userId, authentication);
    }

    @DeleteMapping("/{userId}")
    public String unblock(
            @PathVariable Long userId,
            Authentication authentication
    ) {
        return blockService.unblockUser(userId, authentication);
    }
}
