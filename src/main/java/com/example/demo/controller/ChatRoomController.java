package com.example.demo.controller;

import com.example.demo.dto.ChatRoomDto;
import com.example.demo.dto.ChatRoomMessageDto;
import com.example.demo.dto.PostWebRtcSignalRequest;
import com.example.demo.dto.WebRtcSignalDto;
import com.example.demo.dto.RoomCallSessionDto;
import com.example.demo.service.ChatRoomService;
import com.example.demo.service.RoomCallService;
import com.example.demo.service.WebRtcSignalingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/chat-rooms")
@RequiredArgsConstructor
@CrossOrigin("*")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;
    private final RoomCallService roomCallService;
    private final WebRtcSignalingService webRtcSignalingService;

    @PostMapping
    public ChatRoomDto create(
            @RequestBody Map<String, Object> body,
            Authentication authentication
    ) {
        String name = (String) body.get("name");
        String emoji = (String) body.getOrDefault("emoji", "💬");
        @SuppressWarnings("unchecked")
        List<Number> rawIds = (List<Number>) body.get("memberIds");
        List<Long> memberIds = rawIds != null
                ? rawIds.stream().map(Number::longValue).toList()
                : List.of();

        return chatRoomService.createRoom(name, emoji, memberIds, authentication);
    }

    @GetMapping("/my")
    public List<ChatRoomDto> myRooms(Authentication authentication) {
        return chatRoomService.myRooms(authentication);
    }

    @GetMapping("/{roomId}")
    public ChatRoomDto getRoom(
            @PathVariable Long roomId,
            Authentication authentication
    ) {
        return chatRoomService.getRoom(roomId, authentication);
    }

    @GetMapping("/{roomId}/calls/active")
    public ResponseEntity<RoomCallSessionDto> activeRoomCall(
            @PathVariable Long roomId,
            Authentication authentication
    ) {
        RoomCallSessionDto dto = roomCallService.getActiveCallForRoom(roomId, authentication);
        if (dto == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(dto);
    }

    @PostMapping("/{roomId}/calls/start")
    public RoomCallSessionDto startRoomCall(
            @PathVariable Long roomId,
            @RequestBody(required = false) Map<String, String> body,
            Authentication authentication
    ) {
        String callType = body != null ? body.getOrDefault("callType", "VIDEO") : "VIDEO";
        return roomCallService.startCall(roomId, callType, authentication);
    }

    @GetMapping("/{roomId}/messages")
    public List<ChatRoomMessageDto> messages(
            @PathVariable Long roomId,
            Authentication authentication
    ) {
        return chatRoomService.getMessages(roomId, authentication);
    }

    @PostMapping("/{roomId}/messages")
    public ChatRoomMessageDto sendMessage(
            @PathVariable Long roomId,
            @RequestBody Map<String, String> body,
            Authentication authentication
    ) {
        return chatRoomService.sendMessage(roomId, body.get("content"), authentication);
    }

    @PostMapping("/{roomId}/end")
    public ChatRoomDto endRoom(
            @PathVariable Long roomId,
            Authentication authentication
    ) {
        return chatRoomService.endRoom(roomId, authentication);
    }

    @PostMapping("/{roomId}/leave")
    public String leaveRoom(
            @PathVariable Long roomId,
            Authentication authentication
    ) {
        chatRoomService.leaveRoom(roomId, authentication);
        return "Left room";
    }

    @PostMapping("/join/{inviteCode}")
    public ChatRoomDto joinByInvite(
            @PathVariable String inviteCode,
            Authentication authentication
    ) {
        return chatRoomService.joinByInviteCode(inviteCode, authentication);
    }

    @PostMapping("/{roomId}/members")
    public ChatRoomDto addMember(
            @PathVariable Long roomId,
            @RequestBody Map<String, Long> body,
            Authentication authentication
    ) {
        return chatRoomService.addMember(roomId, body.get("userId"), authentication);
    }

    @PostMapping("/{roomId}/webrtc")
    public WebRtcSignalDto postRoomWebRtc(
            @PathVariable Long roomId,
            @RequestBody PostWebRtcSignalRequest body,
            Authentication authentication
    ) {
        return webRtcSignalingService.postRoomSignal(roomId, body, authentication);
    }

    @GetMapping("/{roomId}/webrtc")
    public List<WebRtcSignalDto> getRoomWebRtc(
            @PathVariable Long roomId,
            @RequestParam(defaultValue = "0") Long afterId,
            Authentication authentication
    ) {
        return webRtcSignalingService.getRoomSignals(roomId, afterId, authentication);
    }
}
