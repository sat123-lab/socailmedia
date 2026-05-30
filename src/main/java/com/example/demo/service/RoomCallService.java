package com.example.demo.service;

import com.example.demo.dto.RoomCallJoinRequestDto;
import com.example.demo.dto.RoomCallSessionDto;
import com.example.demo.entity.*;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomCallService {

    private static final int RING_TIMEOUT_SECONDS = 45;
    private static final List<String> LIVE_STATUSES = List.of("RINGING", "ACTIVE");

    private final RoomCallSessionRepository sessionRepository;
    private final RoomCallParticipantRepository participantRepository;
    private final RoomCallJoinRequestRepository joinRequestRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    private User currentUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public RoomCallSessionDto getActiveCallForRoom(Long roomId, Authentication auth) {
        User me = currentUser(auth);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        ensureMember(room, me);

        Optional<RoomCallSession> active =
                sessionRepository.findFirstByRoomAndStatusInOrderByCreatedAtDesc(room, LIVE_STATUSES);
        return active.map(s -> toDto(s, me)).orElse(null);
    }

    public List<RoomCallSessionDto> getMyLiveCalls(Authentication auth) {
        User me = currentUser(auth);
        List<RoomCallSessionDto> result = new ArrayList<>();
        for (ChatRoomMember m : chatRoomMemberRepository.findByUserIdWithRoom(me.getId())) {
            ChatRoom room = m.getRoom();
            sessionRepository
                    .findFirstByRoomAndStatusInOrderByCreatedAtDesc(room, LIVE_STATUSES)
                    .ifPresent(s -> result.add(toDto(s, me)));
        }
        return result;
    }

    @Transactional
    public RoomCallSessionDto startCall(Long roomId, String callType, Authentication auth) {
        User me = currentUser(auth);
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        ensureMember(room, me);

        Optional<RoomCallSession> existing =
                sessionRepository.findFirstByRoomAndStatusInOrderByCreatedAtDesc(room, LIVE_STATUSES);
        if (existing.isPresent()) {
            throw new RuntimeException("A room call is already in progress");
        }

        String type = callType != null && callType.equalsIgnoreCase("VIDEO") ? "VIDEO" : "VOICE";

        RoomCallSession session = RoomCallSession.builder()
                .room(room)
                .starter(me)
                .callType(type)
                .status("ACTIVE")
                .build();
        session = sessionRepository.save(session);

        List<ChatRoomMember> members = chatRoomMemberRepository.findByRoom(room);
        for (ChatRoomMember m : members) {
            User member = m.getUser();
            boolean isStarter = member.getId().equals(me.getId());
            participantRepository.save(RoomCallParticipant.builder()
                    .session(session)
                    .user(member)
                    .status(isStarter ? "JOINED" : "PENDING")
                    .respondedAt(isStarter ? LocalDateTime.now() : null)
                    .build());

            if (!isStarter) {
                notifyMember(member, me, "ROOM_CALL",
                        buildIncomingMessage(me.getName(), room.getName(), type),
                        session.getId());
            }
        }

        return toDto(session, me);
    }

    public List<RoomCallSessionDto> getIncomingCalls(Authentication auth) {
        User me = currentUser(auth);
        return participantRepository.findPendingIncomingForUser(me).stream()
                .map(RoomCallParticipant::getSession)
                .distinct()
                .map(s -> toDto(s, me))
                .collect(Collectors.toList());
    }

    public RoomCallSessionDto getCall(Long callId, Authentication auth) {
        User me = currentUser(auth);
        RoomCallSession session = getSession(callId);
        ensureMember(session.getRoom(), me);
        return toDto(session, me);
    }

    @Transactional
    public RoomCallSessionDto joinCall(Long callId, Authentication auth) {
        User me = currentUser(auth);
        RoomCallSession session = getSession(callId);
        ensureMember(session.getRoom(), me);
        ensureLive(session);

        RoomCallParticipant p = getOrCreateParticipant(session, me);

        if ("JOINED".equals(p.getStatus())) {
            return toDto(session, me);
        }

        if ("LEFT".equals(p.getStatus())) {
            boolean approved = joinRequestRepository
                    .findBySessionAndUserAndStatus(session, me, "APPROVED")
                    .isPresent();
            if (!approved) {
                throw new RuntimeException("Send a join request to re-enter the call");
            }
        } else if ("DECLINED".equals(p.getStatus()) || "MISSED".equals(p.getStatus())) {
            throw new RuntimeException("Send a join request to enter the call");
        }

        p.setStatus("JOINED");
        p.setRespondedAt(LocalDateTime.now());
        participantRepository.save(p);

        joinRequestRepository.findBySessionAndUserAndStatus(session, me, "PENDING")
                .ifPresent(r -> {
                    r.setStatus("APPROVED");
                    joinRequestRepository.save(r);
                });

        return toDto(session, me);
    }

    @Transactional
    public RoomCallSessionDto declineCall(Long callId, Authentication auth) {
        User me = currentUser(auth);
        RoomCallSession session = getSession(callId);
        RoomCallParticipant p = getOrCreateParticipant(session, me);

        p.setStatus("DECLINED");
        p.setRespondedAt(LocalDateTime.now());
        participantRepository.save(p);

        if (participantRepository.countBySessionAndStatus(session, "JOINED") == 0) {
            endSession(session, false);
        }

        return toDto(session, me);
    }

    @Transactional
    public RoomCallSessionDto leaveCall(Long callId, Authentication auth) {
        User me = currentUser(auth);
        RoomCallSession session = getSession(callId);

        if (session.getStarter().getId().equals(me.getId())) {
            return endCall(callId, auth);
        }

        RoomCallParticipant p = getOrCreateParticipant(session, me);

        if ("JOINED".equals(p.getStatus())) {
            p.setStatus("LEFT");
            p.setRespondedAt(LocalDateTime.now());
            participantRepository.save(p);

            String typeLabel = "VIDEO".equals(session.getCallType()) ? "video" : "voice";
            notifyMember(
                    session.getStarter(),
                    me,
                    "ROOM_CALL_LEFT",
                    me.getName() + " left the room " + typeLabel + " call",
                    session.getRoom().getId()
            );
        }

        long stillJoined = participantRepository.countBySessionAndStatus(session, "JOINED");
        if (stillJoined == 0) {
            markMissedForPending(session);
            endSession(session, false);
        }

        return toDto(session, me);
    }

    @Transactional
    public RoomCallSessionDto endCall(Long callId, Authentication auth) {
        User me = currentUser(auth);
        RoomCallSession session = getSession(callId);

        if (!session.getStarter().getId().equals(me.getId())) {
            throw new RuntimeException("Only the call starter can end the call for everyone");
        }

        notifyCallEnded(session);
        endSession(session, true);
        return toDto(session, me);
    }

    @Transactional
    public RoomCallJoinRequestDto requestJoin(Long callId, Authentication auth) {
        User me = currentUser(auth);
        RoomCallSession session = getSession(callId);
        ensureMember(session.getRoom(), me);
        ensureLive(session);

        if (session.getStarter().getId().equals(me.getId())) {
            throw new RuntimeException("You are already the call host");
        }

        RoomCallParticipant p = getOrCreateParticipant(session, me);
        if ("JOINED".equals(p.getStatus())) {
            throw new RuntimeException("You are already in the call");
        }

        Optional<RoomCallJoinRequest> existing =
                joinRequestRepository.findBySessionAndUserAndStatus(session, me, "PENDING");
        if (existing.isPresent()) {
            return RoomCallJoinRequestDto.from(existing.get());
        }

        RoomCallJoinRequest request = joinRequestRepository.save(RoomCallJoinRequest.builder()
                .session(session)
                .user(me)
                .status("PENDING")
                .build());

        User starter = session.getStarter();
        notifyMember(starter, me, "ROOM_CALL_JOIN_REQUEST",
                me.getName() + " wants to join the room call in \""
                        + session.getRoom().getName() + "\"",
                session.getId());

        return RoomCallJoinRequestDto.from(request);
    }

    public List<RoomCallJoinRequestDto> getPendingJoinRequests(Long callId, Authentication auth) {
        User me = currentUser(auth);
        RoomCallSession session = getSession(callId);
        if (!session.getStarter().getId().equals(me.getId())) {
            throw new RuntimeException("Only the call starter can view join requests");
        }
        return joinRequestRepository.findBySessionAndStatusOrderByCreatedAtAsc(session, "PENDING")
                .stream()
                .map(RoomCallJoinRequestDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public RoomCallJoinRequestDto approveJoinRequest(Long requestId, Authentication auth) {
        User me = currentUser(auth);
        RoomCallJoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Join request not found"));
        RoomCallSession session = request.getSession();

        if (!session.getStarter().getId().equals(me.getId())) {
            throw new RuntimeException("Only the call starter can approve requests");
        }
        if (!"PENDING".equals(request.getStatus())) {
            throw new RuntimeException("Request is no longer pending");
        }
        ensureLive(session);

        request.setStatus("APPROVED");
        joinRequestRepository.save(request);

        User requester = request.getUser();
        RoomCallParticipant p = getOrCreateParticipant(session, requester);
        p.setStatus("JOINED");
        p.setRespondedAt(LocalDateTime.now());
        participantRepository.save(p);

        notifyMember(requester, me, "ROOM_CALL_JOIN_APPROVED",
                "You can join the room call in \"" + session.getRoom().getName() + "\"",
                session.getId());

        return RoomCallJoinRequestDto.from(request);
    }

    @Transactional
    public RoomCallJoinRequestDto declineJoinRequest(Long requestId, Authentication auth) {
        User me = currentUser(auth);
        RoomCallJoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Join request not found"));
        RoomCallSession session = request.getSession();

        if (!session.getStarter().getId().equals(me.getId())) {
            throw new RuntimeException("Only the call starter can decline requests");
        }

        request.setStatus("DECLINED");
        joinRequestRepository.save(request);
        return RoomCallJoinRequestDto.from(request);
    }

    @Scheduled(fixedRate = 15000)
    @Transactional
    public void expireRingingRoomCalls() {
        List<RoomCallSession> live = sessionRepository.findByStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.now();

        for (RoomCallSession session : live) {
            if (session.getCreatedAt() == null) continue;
            if (Duration.between(session.getCreatedAt(), now).getSeconds() < RING_TIMEOUT_SECONDS) {
                continue;
            }

            long pending = participantRepository.countBySessionAndStatus(session, "PENDING");
            if (pending == 0) continue;

            markMissedForPending(session);

            long joined = participantRepository.countBySessionAndStatus(session, "JOINED");
            if (joined == 0) {
                endSession(session, false);
            }
        }
    }

    private void notifyCallEnded(RoomCallSession session) {
        ChatRoom room = session.getRoom();
        User starter = session.getStarter();
        String typeLabel = "VIDEO".equals(session.getCallType()) ? "video" : "voice";

        for (RoomCallParticipant p : participantRepository.findBySession(session)) {
            if (p.getUser().getId().equals(starter.getId())) continue;
            if (!"JOINED".equals(p.getStatus()) && !"PENDING".equals(p.getStatus())) continue;
            notifyMember(p.getUser(), starter, "ROOM_CALL_ENDED",
                    "Room " + typeLabel + " call in \"" + room.getName() + "\" ended",
                    room.getId());
        }
    }

    private void markMissedForPending(RoomCallSession session) {
        ChatRoom room = session.getRoom();
        User starter = session.getStarter();
        String typeLabel = "VIDEO".equals(session.getCallType()) ? "video" : "voice";

        for (RoomCallParticipant p : participantRepository.findBySession(session)) {
            if (!"PENDING".equals(p.getStatus())) continue;

            p.setStatus("MISSED");
            p.setRespondedAt(LocalDateTime.now());
            participantRepository.save(p);

            notifyMember(p.getUser(), starter, "MISSED_ROOM_CALL",
                    "Missed room " + typeLabel + " call in \"" + room.getName() + "\"",
                    room.getId());
        }
    }

    private void endSession(RoomCallSession session, boolean notifyMissed) {
        if (notifyMissed) {
            markMissedForPending(session);
        }
        session.setStatus("ENDED");
        session.setEndedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    private RoomCallParticipant getOrCreateParticipant(RoomCallSession session, User user) {
        return participantRepository.findBySessionAndUser(session, user)
                .orElseGet(() -> participantRepository.save(RoomCallParticipant.builder()
                        .session(session)
                        .user(user)
                        .status("PENDING")
                        .build()));
    }

    private RoomCallSessionDto toDto(RoomCallSession session, User me) {
        List<Long> joined = participantRepository.findBySession(session).stream()
                .filter(p -> "JOINED".equals(p.getStatus()))
                .map(p -> p.getUser().getId())
                .collect(Collectors.toList());

        String myStatus = participantRepository.findBySessionAndUser(session, me)
                .map(RoomCallParticipant::getStatus)
                .orElse("NONE");

        boolean isStarter = session.getStarter().getId().equals(me.getId());
        boolean hasPendingRequest = joinRequestRepository
                .findBySessionAndUserAndStatus(session, me, "PENDING")
                .isPresent();
        int pendingCount = isStarter
                ? (int) joinRequestRepository.countBySessionAndStatus(session, "PENDING")
                : 0;

        return RoomCallSessionDto.from(
                session, myStatus, joined, isStarter, hasPendingRequest, pendingCount);
    }

    private RoomCallSession getSession(Long callId) {
        return sessionRepository.findById(callId)
                .orElseThrow(() -> new RuntimeException("Room call not found"));
    }

    private void ensureLive(RoomCallSession session) {
        if (!LIVE_STATUSES.contains(session.getStatus())) {
            throw new RuntimeException("This call has ended");
        }
    }

    private void ensureMember(ChatRoom room, User user) {
        if (!chatRoomMemberRepository.existsByRoomAndUser(room, user)) {
            throw new RuntimeException("Not a member of this room");
        }
    }

    private String buildIncomingMessage(String starterName, String roomName, String callType) {
        String media = "VIDEO".equals(callType) ? "video" : "voice";
        return starterName + " started a room " + media + " call in \"" + roomName + "\"";
    }

    private void notifyMember(User receiver, User sender, String type, String message, Long relatedId) {
        notificationRepository.save(Notification.builder()
                .receiver(receiver)
                .sender(sender)
                .type(type)
                .message(message)
                .relatedId(relatedId)
                .read(false)
                .build());
    }
}
