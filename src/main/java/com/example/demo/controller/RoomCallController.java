package com.example.demo.controller;

import com.example.demo.dto.RoomCallJoinRequestDto;
import com.example.demo.dto.RoomCallSessionDto;
import com.example.demo.service.RoomCallService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/chat-rooms/calls")
@RequiredArgsConstructor
@CrossOrigin("*")
public class RoomCallController {

    private final RoomCallService roomCallService;

    @GetMapping("/incoming")
    public List<RoomCallSessionDto> incoming(Authentication auth) {
        return roomCallService.getIncomingCalls(auth);
    }

    @GetMapping("/my-live")
    public List<RoomCallSessionDto> myLiveCalls(Authentication auth) {
        return roomCallService.getMyLiveCalls(auth);
    }

    @GetMapping("/{callId}")
    public RoomCallSessionDto getCall(
            @PathVariable Long callId,
            Authentication auth
    ) {
        return roomCallService.getCall(callId, auth);
    }

    @PostMapping("/{callId}/join")
    public RoomCallSessionDto join(
            @PathVariable Long callId,
            Authentication auth
    ) {
        return roomCallService.joinCall(callId, auth);
    }

    @PostMapping("/{callId}/decline")
    public RoomCallSessionDto decline(
            @PathVariable Long callId,
            Authentication auth
    ) {
        return roomCallService.declineCall(callId, auth);
    }

    @PostMapping("/{callId}/leave")
    public RoomCallSessionDto leave(
            @PathVariable Long callId,
            Authentication auth
    ) {
        return roomCallService.leaveCall(callId, auth);
    }

    @PostMapping("/{callId}/end")
    public RoomCallSessionDto end(
            @PathVariable Long callId,
            Authentication auth
    ) {
        return roomCallService.endCall(callId, auth);
    }

    @PostMapping("/{callId}/request-join")
    public RoomCallJoinRequestDto requestJoin(
            @PathVariable Long callId,
            Authentication auth
    ) {
        return roomCallService.requestJoin(callId, auth);
    }

    @GetMapping("/{callId}/join-requests")
    public List<RoomCallJoinRequestDto> joinRequests(
            @PathVariable Long callId,
            Authentication auth
    ) {
        return roomCallService.getPendingJoinRequests(callId, auth);
    }

    @PostMapping("/join-requests/{requestId}/approve")
    public RoomCallJoinRequestDto approveJoin(
            @PathVariable Long requestId,
            Authentication auth
    ) {
        return roomCallService.approveJoinRequest(requestId, auth);
    }

    @PostMapping("/join-requests/{requestId}/decline")
    public RoomCallJoinRequestDto declineJoin(
            @PathVariable Long requestId,
            Authentication auth
    ) {
        return roomCallService.declineJoinRequest(requestId, auth);
    }
}
