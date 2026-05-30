package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.service.GroupCallService;
import com.example.demo.service.SocialGroupService;
import com.example.demo.service.WebRtcSignalingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
@CrossOrigin("*")
public class SocialGroupController {

    private final SocialGroupService socialGroupService;
    private final WebRtcSignalingService webRtcSignalingService;
    private final GroupCallService groupCallService;

    @PostMapping
    public GroupSummaryDto create(@RequestBody CreateGroupRequest request, Authentication auth) {
        return socialGroupService.createGroup(request, auth);
    }

    @GetMapping("/my")
    public List<GroupSummaryDto> myGroups(Authentication auth) {
        return socialGroupService.myGroups(auth);
    }

    @GetMapping("/{groupId}")
    public GroupDetailDto detail(@PathVariable Long groupId, Authentication auth) {
        return socialGroupService.getGroup(groupId, auth);
    }

    @PatchMapping("/{groupId}")
    public GroupDetailDto update(
            @PathVariable Long groupId,
            @RequestBody UpdateGroupRequest body,
            Authentication auth
    ) {
        return socialGroupService.updateGroup(groupId, body, auth);
    }

    @PostMapping("/{groupId}/members")
    public GroupDetailDto addMember(
            @PathVariable Long groupId,
            @RequestBody AddGroupMemberRequest body,
            Authentication auth
    ) {
        return socialGroupService.addMember(groupId, body, auth);
    }

    @GetMapping("/{groupId}/messages")
    public List<GroupMessageDto> messages(@PathVariable Long groupId, Authentication auth) {
        return socialGroupService.getMessages(groupId, auth);
    }

    @PostMapping("/{groupId}/messages")
    public GroupMessageDto sendMessage(
            @PathVariable Long groupId,
            @RequestBody SendGroupMessageRequest body,
            Authentication auth
    ) {
        return socialGroupService.sendMessage(groupId, body, auth);
    }

    @GetMapping("/{groupId}/calls/active")
    public org.springframework.http.ResponseEntity<GroupCallSessionDto> activeGroupCall(
            @PathVariable Long groupId,
            Authentication auth
    ) {
        GroupCallSessionDto dto = groupCallService.getActiveCallForGroup(groupId, auth);
        if (dto == null) {
            return org.springframework.http.ResponseEntity.noContent().build();
        }
        return org.springframework.http.ResponseEntity.ok(dto);
    }

    @PostMapping("/{groupId}/calls/start")
    public GroupCallSessionDto startGroupCall(
            @PathVariable Long groupId,
            @RequestBody(required = false) java.util.Map<String, String> body,
            Authentication auth
    ) {
        String callType = body != null ? body.get("callType") : "VOICE";
        return groupCallService.startCall(groupId, callType, auth);
    }

    @PostMapping("/{groupId}/webrtc")
    public WebRtcSignalDto postGroupWebRtc(
            @PathVariable Long groupId,
            @RequestBody PostWebRtcSignalRequest body,
            Authentication auth
    ) {
        return webRtcSignalingService.postGroupSignal(groupId, body, auth);
    }

    @GetMapping("/{groupId}/webrtc")
    public List<WebRtcSignalDto> getGroupWebRtc(
            @PathVariable Long groupId,
            @RequestParam(defaultValue = "0") Long afterId,
            Authentication auth
    ) {
        return webRtcSignalingService.getGroupSignals(groupId, afterId, auth);
    }
}
