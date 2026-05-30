package com.example.demo.controller;

import com.example.demo.dto.CallSessionDto;
import com.example.demo.dto.PostWebRtcSignalRequest;
import com.example.demo.dto.WebRtcSignalDto;
import com.example.demo.service.CallService;
import com.example.demo.service.WebRtcSignalingService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/calls")
@RequiredArgsConstructor
@CrossOrigin("*")
public class CallController {

    private final CallService callService;
    private final WebRtcSignalingService webRtcSignalingService;

    @PostMapping("/start")
    public CallSessionDto start(
            @RequestBody Map<String, Object> body,
            Authentication authentication
    ) {
        Long receiverId = ((Number) body.get("receiverId")).longValue();
        String callType = (String) body.getOrDefault("callType", "VOICE");
        return callService.startCall(receiverId, callType, authentication);
    }

    @GetMapping("/pending")
    public List<CallSessionDto> pending(Authentication authentication) {
        return callService.getPendingCalls(authentication);
    }

    /** Clears ghost RINGING/ACTIVE rows for the logged-in user. */
    @PostMapping("/release-stale")
    public Map<String, Object> releaseStale(Authentication authentication) {
        callService.releaseStaleForCurrentUser(authentication);
        return Map.of("ok", true);
    }

    @GetMapping("/{callId}")
    public CallSessionDto get(
            @PathVariable Long callId,
            Authentication authentication
    ) {
        return callService.getCall(callId, authentication);
    }

    @PostMapping("/{callId}/accept")
    public CallSessionDto accept(
            @PathVariable Long callId,
            Authentication authentication
    ) {
        return callService.acceptCall(callId, authentication);
    }

    @PostMapping("/{callId}/decline")
    public CallSessionDto decline(
            @PathVariable Long callId,
            Authentication authentication
    ) {
        return callService.declineCall(callId, authentication);
    }

    @PostMapping("/{callId}/end")
    public CallSessionDto end(
            @PathVariable Long callId,
            Authentication authentication
    ) {
        return callService.endCall(callId, authentication);
    }

    @PostMapping("/{callId}/webrtc")
    public WebRtcSignalDto postWebRtc(
            @PathVariable Long callId,
            @RequestBody PostWebRtcSignalRequest body,
            Authentication authentication
    ) {
        return webRtcSignalingService.postCallSignal(callId, body, authentication);
    }

    @GetMapping("/{callId}/webrtc")
    public List<WebRtcSignalDto> getWebRtc(
            @PathVariable Long callId,
            @RequestParam(defaultValue = "0") Long afterId,
            Authentication authentication
    ) {
        return webRtcSignalingService.getCallSignals(callId, afterId, authentication);
    }
}
