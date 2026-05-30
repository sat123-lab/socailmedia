package com.example.demo.service;

import com.example.demo.dto.PostWebRtcSignalRequest;
import com.example.demo.dto.WebRtcSignalDto;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WebRtcSignalingService {

    public static final String CTX_CALL = "CALL";
    public static final String CTX_ROOM = "ROOM";
    public static final String CTX_GROUP = "GROUP";

    private final WebRtcSignalRepository signalRepository;
    private final CallSessionRepository callSessionRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final SocialGroupMemberRepository socialGroupMemberRepository;
    private final SocialGroupRepository socialGroupRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    private User currentUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public WebRtcSignalDto postCallSignal(
            Long callId,
            PostWebRtcSignalRequest body,
            Authentication auth
    ) {
        User me = currentUser(auth);
        CallSession session = callSessionRepository.findById(callId)
                .orElseThrow(() -> new RuntimeException("Call not found"));

        boolean participant = session.getCaller().getId().equals(me.getId())
                || session.getReceiver().getId().equals(me.getId());
        if (!participant) {
            throw new RuntimeException("Not authorized");
        }

        validate(body);

        Long toUserId = body.getToUserId();
        if (toUserId == null) {
            toUserId = me.getId().equals(session.getCaller().getId())
                    ? session.getReceiver().getId()
                    : session.getCaller().getId();
        }

        WebRtcSignal saved = signalRepository.save(WebRtcSignal.builder()
                .contextType(CTX_CALL)
                .contextId(callId)
                .fromUserId(me.getId())
                .toUserId(toUserId)
                .signalType(body.getSignalType().toLowerCase())
                .payload(body.getPayload())
                .build());

        return WebRtcSignalDto.from(saved);
    }

    public List<WebRtcSignalDto> getCallSignals(Long callId, Long afterId, Authentication auth) {
        User me = currentUser(auth);
        CallSession session = callSessionRepository.findById(callId)
                .orElseThrow(() -> new RuntimeException("Call not found"));

        boolean participant = session.getCaller().getId().equals(me.getId())
                || session.getReceiver().getId().equals(me.getId());
        if (!participant) {
            throw new RuntimeException("Not authorized");
        }

        long cursor = afterId != null ? afterId : 0L;
        return signalRepository
                .findByContextTypeAndContextIdAndIdGreaterThanOrderByIdAsc(CTX_CALL, callId, cursor)
                .stream()
                .filter(s -> isForUser(s, me.getId()))
                .map(WebRtcSignalDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public WebRtcSignalDto postRoomSignal(
            Long roomId,
            PostWebRtcSignalRequest body,
            Authentication auth
    ) {
        User me = currentUser(auth);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        ensureRoomMember(room, me);

        validate(body);

        WebRtcSignal saved = signalRepository.save(WebRtcSignal.builder()
                .contextType(CTX_ROOM)
                .contextId(roomId)
                .fromUserId(me.getId())
                .toUserId(body.getToUserId())
                .signalType(body.getSignalType().toLowerCase())
                .payload(body.getPayload())
                .build());

        return WebRtcSignalDto.from(saved);
    }

    public List<WebRtcSignalDto> getRoomSignals(Long roomId, Long afterId, Authentication auth) {
        User me = currentUser(auth);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        ensureRoomMember(room, me);

        long cursor = afterId != null ? afterId : 0L;
        return signalRepository
                .findByContextTypeAndContextIdAndIdGreaterThanOrderByIdAsc(CTX_ROOM, roomId, cursor)
                .stream()
                .filter(s -> isForUser(s, me.getId()))
                .map(WebRtcSignalDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public WebRtcSignalDto postGroupSignal(
            Long groupId,
            PostWebRtcSignalRequest body,
            Authentication auth
    ) {
        User me = currentUser(auth);
        SocialGroup group = socialGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        ensureGroupMember(group, me);

        validate(body);

        WebRtcSignal saved = signalRepository.save(WebRtcSignal.builder()
                .contextType(CTX_GROUP)
                .contextId(groupId)
                .fromUserId(me.getId())
                .toUserId(body.getToUserId())
                .signalType(body.getSignalType().toLowerCase())
                .payload(body.getPayload())
                .build());

        return WebRtcSignalDto.from(saved);
    }

    public List<WebRtcSignalDto> getGroupSignals(Long groupId, Long afterId, Authentication auth) {
        User me = currentUser(auth);
        SocialGroup group = socialGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        ensureGroupMember(group, me);

        long cursor = afterId != null ? afterId : 0L;
        return signalRepository
                .findByContextTypeAndContextIdAndIdGreaterThanOrderByIdAsc(CTX_GROUP, groupId, cursor)
                .stream()
                .filter(s -> isForUser(s, me.getId()))
                .map(WebRtcSignalDto::from)
                .collect(Collectors.toList());
    }

    private void ensureRoomMember(ChatRoom room, User user) {
        if (!chatRoomMemberRepository.existsByRoomAndUser(room, user)) {
            throw new RuntimeException("Not a member of this room");
        }
    }

    private void ensureGroupMember(SocialGroup group, User user) {
        if (!socialGroupMemberRepository.existsByGroupAndUser(group, user)) {
            throw new RuntimeException("Not a member of this group");
        }
    }

    private void notifyGroupCallStarted(SocialGroup group, User starter) {
        for (SocialGroupMember m : socialGroupMemberRepository.findByGroup(group)) {
            User r = m.getUser();
            if (r.getId().equals(starter.getId())) {
                continue;
            }
            notificationRepository.save(Notification.builder()
                    .receiver(r)
                    .sender(starter)
                    .type("GROUP_CALL")
                    .message(starter.getName() + " started a call in \"" + group.getName() + "\"")
                    .relatedId(group.getId())
                    .read(false)
                    .build());
        }
    }

    private boolean isForUser(WebRtcSignal s, Long userId) {
        if (s.getFromUserId().equals(userId)) {
            return false;
        }
        if (s.getToUserId() == null) {
            return true;
        }
        return s.getToUserId().equals(userId);
    }

    private void validate(PostWebRtcSignalRequest body) {
        if (body.getSignalType() == null || body.getSignalType().isBlank()) {
            throw new RuntimeException("signalType is required");
        }
        if (body.getPayload() == null || body.getPayload().isBlank()) {
            throw new RuntimeException("payload is required");
        }
        String t = body.getSignalType().toLowerCase();
        if (!t.equals("offer") && !t.equals("answer") && !t.equals("ice")
                && !t.equals("join") && !t.equals("leave")) {
            throw new RuntimeException("Invalid signalType");
        }
    }
}
