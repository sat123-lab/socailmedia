package com.example.demo.controller;

import com.example.demo.dto.GroupCallJoinRequestDto;
import com.example.demo.dto.GroupCallSessionDto;
import com.example.demo.service.GroupCallService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/groups/calls")
@RequiredArgsConstructor
@CrossOrigin("*")
public class GroupCallController {

    private final GroupCallService groupCallService;

    @GetMapping("/incoming")
    public List<GroupCallSessionDto> incoming(Authentication auth) {
        return groupCallService.getIncomingCalls(auth);
    }

    @GetMapping("/my-live")
    public List<GroupCallSessionDto> myLiveCalls(Authentication auth) {
        return groupCallService.getMyLiveCalls(auth);
    }

    @GetMapping("/{callId}")
    public GroupCallSessionDto getCall(
            @PathVariable Long callId,
            Authentication auth
    ) {
        return groupCallService.getCall(callId, auth);
    }

    @PostMapping("/{callId}/join")
    public GroupCallSessionDto join(
            @PathVariable Long callId,
            Authentication auth
    ) {
        return groupCallService.joinCall(callId, auth);
    }

    @PostMapping("/{callId}/decline")
    public GroupCallSessionDto decline(
            @PathVariable Long callId,
            Authentication auth
    ) {
        return groupCallService.declineCall(callId, auth);
    }

    @PostMapping("/{callId}/leave")
    public GroupCallSessionDto leave(
            @PathVariable Long callId,
            Authentication auth
    ) {
        return groupCallService.leaveCall(callId, auth);
    }

    @PostMapping("/{callId}/end")
    public GroupCallSessionDto end(
            @PathVariable Long callId,
            Authentication auth
    ) {
        return groupCallService.endCall(callId, auth);
    }

    @PostMapping("/{callId}/request-join")
    public GroupCallJoinRequestDto requestJoin(
            @PathVariable Long callId,
            Authentication auth
    ) {
        return groupCallService.requestJoin(callId, auth);
    }

    @GetMapping("/{callId}/join-requests")
    public List<GroupCallJoinRequestDto> joinRequests(
            @PathVariable Long callId,
            Authentication auth
    ) {
        return groupCallService.getPendingJoinRequests(callId, auth);
    }

    @PostMapping("/join-requests/{requestId}/approve")
    public GroupCallJoinRequestDto approveJoin(
            @PathVariable Long requestId,
            Authentication auth
    ) {
        return groupCallService.approveJoinRequest(requestId, auth);
    }

    @PostMapping("/join-requests/{requestId}/decline")
    public GroupCallJoinRequestDto declineJoin(
            @PathVariable Long requestId,
            Authentication auth
    ) {
        return groupCallService.declineJoinRequest(requestId, auth);
    }
}
