package com.example.demo.controller;

import com.example.demo.dto.ConversationDto;
import com.example.demo.dto.SendMessageRequest;
import com.example.demo.entity.ChatMessage;
import com.example.demo.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
@CrossOrigin("*")
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/presence")
    public String updatePresence(Authentication authentication) {
        messageService.updatePresence(authentication.getName());
        return "ok";
    }

    @GetMapping("/conversations")
    public List<ConversationDto> getConversations(
            Authentication authentication
    ) {
        return messageService.getConversations(authentication.getName());
    }

    @GetMapping("/with/{userId}")
    public List<ChatMessage> getMessages(
            @PathVariable Long userId,
            Authentication authentication
    ) {
        messageService.updatePresence(authentication.getName());
        return messageService.getMessages(authentication.getName(), userId);
    }

    @PostMapping("/send")
    public ChatMessage sendMessage(
            @RequestBody SendMessageRequest request,
            Authentication authentication
    ) {
        // Use the rich overload so encrypted envelopes + disappearing TTL
        // flow through to the entity.
        return messageService.sendMessage(authentication.getName(), request);
    }
}
