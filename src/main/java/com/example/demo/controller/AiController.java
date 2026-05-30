package com.example.demo.controller;

import com.example.demo.dto.AiAskRequest;
import com.example.demo.dto.AiResponseDto;
import com.example.demo.service.AiAssistantService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AiController {

    private final AiAssistantService aiAssistantService;

    @GetMapping("/suggest-videos")
    public AiResponseDto suggestVideos(Authentication auth) {
        return aiAssistantService.suggestVideos(auth);
    }

    @GetMapping("/suggest-friends")
    public AiResponseDto suggestFriends(Authentication auth) {
        return aiAssistantService.suggestFriends(auth);
    }

    @GetMapping("/suggest-stickers")
    public AiResponseDto suggestStickers(Authentication auth) {
        return aiAssistantService.suggestStickers(auth);
    }

    @PostMapping("/ask")
    public AiResponseDto ask(@RequestBody AiAskRequest body, Authentication auth) {
        return aiAssistantService.ask(body.getPrompt(), auth);
    }

    @PostMapping("/sticker")
    public AiResponseDto sticker(@RequestBody AiAskRequest body, Authentication auth) {
        return aiAssistantService.stickerForSituation(body.getSituation(), auth);
    }
}
