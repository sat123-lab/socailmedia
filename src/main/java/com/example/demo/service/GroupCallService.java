package com.example.demo.service;

import com.example.demo.dto.GroupCallJoinRequestDto;
import com.example.demo.dto.GroupCallSessionDto;
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
public class GroupCallService {

    private static final int RING_TIMEOUT_SECONDS = 45;
    private static final List<String> LIVE_STATUSES = List.of("RINGING", "ACTIVE");

    private final GroupCallSessionRepository sessionRepository;
    private final GroupCallParticipantRepository participantRepository;
    private final GroupCallJoinRequestRepository joinRequestRepository;
    private final SocialGroupRepository socialGroupRepository;
    private final SocialGroupMemberRepository socialGroupMemberRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;

    private User currentUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public GroupCallSessionDto getActiveCallForGroup(Long groupId, Authentication auth) {
        User me = currentUser(auth);
        SocialGroup group = socialGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        ensureMember(group, me);

        Optional<GroupCallSession> active =
                sessionRepository.findFirstByGroupAndStatusInOrderByCreatedAtDesc(group, LIVE_STATUSES);
        return active.map(s -> toDto(s, me)).orElse(null);
    }

    public List<GroupCallSessionDto> getMyLiveCalls(Authentication auth) {
        User me = currentUser(auth);
        List<GroupCallSessionDto> result = new ArrayList<>();
        for (SocialGroupMember m : socialGroupMemberRepository.findByUserIdWithGroup(me.getId())) {
            SocialGroup group = m.getGroup();
            sessionRepository
                    .findFirstByGroupAndStatusInOrderByCreatedAtDesc(group, LIVE_STATUSES)
                    .ifPresent(s -> result.add(toDto(s, me)));
        }
        return result;
    }

    @Transactional
    public GroupCallSessionDto startCall(Long groupId, String callType, Authentication auth) {
        User me = currentUser(auth);
        SocialGroup group = socialGroupRepository.findById(groupId)
                .orElseThrow(() -> new RuntimeException("Group not found"));
        ensureMember(group, me);

        Optional<GroupCallSession> existing =
                sessionRepository.findFirstByGroupAndStatusInOrderByCreatedAtDesc(group, LIVE_STATUSES);
        if (existing.isPresent()) {
            throw new RuntimeException("A group call is already in progress");
        }

        String type = callType != null && callType.equalsIgnoreCase("VIDEO") ? "VIDEO" : "VOICE";

        GroupCallSession session = GroupCallSession.builder()
                .group(group)
                .starter(me)
                .callType(type)
                .status("ACTIVE")
                .build();
        session = sessionRepository.save(session);

        List<SocialGroupMember> members = socialGroupMemberRepository.findByGroup(group);
        for (SocialGroupMember m : members) {
            User member = m.getUser();
            boolean isStarter = member.getId().equals(me.getId());
            participantRepository.save(GroupCallParticipant.builder()
                    .session(session)
                    .user(member)
                    .status(isStarter ? "JOINED" : "PENDING")
                    .respondedAt(isStarter ? LocalDateTime.now() : null)
                    .build());

            if (!isStarter) {
                notifyMember(member, me, "GROUP_CALL",
                        buildIncomingMessage(me.getName(), group.getName(), type),
                        session.getId());
            }
        }

        return toDto(session, me);
    }

    public List<GroupCallSessionDto> getIncomingCalls(Authentication auth) {
        User me = currentUser(auth);
        return participantRepository.findPendingIncomingForUser(me).stream()
                .map(GroupCallParticipant::getSession)
                .distinct()
                .map(s -> toDto(s, me))
                .collect(Collectors.toList());
    }

    public GroupCallSessionDto getCall(Long callId, Authentication auth) {
        User me = currentUser(auth);
        GroupCallSession session = getSession(callId);
        ensureMember(session.getGroup(), me);
        return toDto(session, me);
    }

    @Transactional
    public GroupCallSessionDto joinCall(Long callId, Authentication auth) {
        User me = currentUser(auth);
        GroupCallSession session = getSession(callId);
        ensureMember(session.getGroup(), me);
        ensureLive(session);

        GroupCallParticipant p = getOrCreateParticipant(session, me);

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
    public GroupCallSessionDto declineCall(Long callId, Authentication auth) {
        User me = currentUser(auth);
        GroupCallSession session = getSession(callId);
        GroupCallParticipant p = getOrCreateParticipant(session, me);

        p.setStatus("DECLINED");
        p.setRespondedAt(LocalDateTime.now());
        participantRepository.save(p);

        if (participantRepository.countBySessionAndStatus(session, "JOINED") == 0) {
            endSession(session, false);
        }

        return toDto(session, me);
    }

    @Transactional
    public GroupCallSessionDto leaveCall(Long callId, Authentication auth) {
        User me = currentUser(auth);
        GroupCallSession session = getSession(callId);

        if (session.getStarter().getId().equals(me.getId())) {
            return endCall(callId, auth);
        }

        GroupCallParticipant p = getOrCreateParticipant(session, me);

        if ("JOINED".equals(p.getStatus())) {
            p.setStatus("LEFT");
            p.setRespondedAt(LocalDateTime.now());
            participantRepository.save(p);

            String typeLabel = "VIDEO".equals(session.getCallType()) ? "video" : "voice";
            notifyMember(
                    session.getStarter(),
                    me,
                    "GROUP_CALL_LEFT",
                    me.getName() + " left the group " + typeLabel + " call",
                    session.getGroup().getId()
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
    public GroupCallSessionDto endCall(Long callId, Authentication auth) {
        User me = currentUser(auth);
        GroupCallSession session = getSession(callId);

        if (!session.getStarter().getId().equals(me.getId())) {
            throw new RuntimeException("Only the call starter can end the call for everyone");
        }

        notifyCallEnded(session);
        endSession(session, true);
        return toDto(session, me);
    }

    @Transactional
    public GroupCallJoinRequestDto requestJoin(Long callId, Authentication auth) {
        User me = currentUser(auth);
        GroupCallSession session = getSession(callId);
        ensureMember(session.getGroup(), me);
        ensureLive(session);

        if (session.getStarter().getId().equals(me.getId())) {
            throw new RuntimeException("You are already the call host");
        }

        GroupCallParticipant p = getOrCreateParticipant(session, me);
        if ("JOINED".equals(p.getStatus())) {
            throw new RuntimeException("You are already in the call");
        }

        Optional<GroupCallJoinRequest> existing =
                joinRequestRepository.findBySessionAndUserAndStatus(session, me, "PENDING");
        if (existing.isPresent()) {
            return GroupCallJoinRequestDto.from(existing.get());
        }

        GroupCallJoinRequest request = joinRequestRepository.save(GroupCallJoinRequest.builder()
                .session(session)
                .user(me)
                .status("PENDING")
                .build());

        User starter = session.getStarter();
        notifyMember(starter, me, "GROUP_CALL_JOIN_REQUEST",
                me.getName() + " wants to join the group call in \""
                        + session.getGroup().getName() + "\"",
                session.getId());

        return GroupCallJoinRequestDto.from(request);
    }

    public List<GroupCallJoinRequestDto> getPendingJoinRequests(Long callId, Authentication auth) {
        User me = currentUser(auth);
        GroupCallSession session = getSession(callId);
        if (!session.getStarter().getId().equals(me.getId())) {
            throw new RuntimeException("Only the call starter can view join requests");
        }
        return joinRequestRepository.findBySessionAndStatusOrderByCreatedAtAsc(session, "PENDING")
                .stream()
                .map(GroupCallJoinRequestDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public GroupCallJoinRequestDto approveJoinRequest(Long requestId, Authentication auth) {
        User me = currentUser(auth);
        GroupCallJoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Join request not found"));
        GroupCallSession session = request.getSession();

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
        GroupCallParticipant p = getOrCreateParticipant(session, requester);
        p.setStatus("JOINED");
        p.setRespondedAt(LocalDateTime.now());
        participantRepository.save(p);

        notifyMember(requester, me, "GROUP_CALL_JOIN_APPROVED",
                "You can join the group call in \"" + session.getGroup().getName() + "\"",
                session.getId());

        return GroupCallJoinRequestDto.from(request);
    }

    @Transactional
    public GroupCallJoinRequestDto declineJoinRequest(Long requestId, Authentication auth) {
        User me = currentUser(auth);
        GroupCallJoinRequest request = joinRequestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Join request not found"));
        GroupCallSession session = request.getSession();

        if (!session.getStarter().getId().equals(me.getId())) {
            throw new RuntimeException("Only the call starter can decline requests");
        }

        request.setStatus("DECLINED");
        joinRequestRepository.save(request);
        return GroupCallJoinRequestDto.from(request);
    }

    @Scheduled(fixedRate = 15000)
    @Transactional
    public void expireRingingGroupCalls() {
        List<GroupCallSession> live = sessionRepository.findByStatus("ACTIVE");
        LocalDateTime now = LocalDateTime.now();

        for (GroupCallSession session : live) {
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

    private void notifyCallEnded(GroupCallSession session) {
        SocialGroup group = session.getGroup();
        User starter = session.getStarter();
        String typeLabel = "VIDEO".equals(session.getCallType()) ? "video" : "voice";

        for (GroupCallParticipant p : participantRepository.findBySession(session)) {
            if (p.getUser().getId().equals(starter.getId())) continue;
            if (!"JOINED".equals(p.getStatus()) && !"PENDING".equals(p.getStatus())) continue;
            notifyMember(p.getUser(), starter, "GROUP_CALL_ENDED",
                    "Group " + typeLabel + " call in \"" + group.getName() + "\" ended",
                    group.getId());
        }
    }

    private void markMissedForPending(GroupCallSession session) {
        SocialGroup group = session.getGroup();
        User starter = session.getStarter();
        String typeLabel = "VIDEO".equals(session.getCallType()) ? "video" : "voice";

        for (GroupCallParticipant p : participantRepository.findBySession(session)) {
            if (!"PENDING".equals(p.getStatus())) continue;

            p.setStatus("MISSED");
            p.setRespondedAt(LocalDateTime.now());
            participantRepository.save(p);

            notifyMember(p.getUser(), starter, "MISSED_GROUP_CALL",
                    "Missed group " + typeLabel + " call in \"" + group.getName() + "\"",
                    group.getId());
        }
    }

    private void endSession(GroupCallSession session, boolean notifyMissed) {
        if (notifyMissed) {
            markMissedForPending(session);
        }
        session.setStatus("ENDED");
        session.setEndedAt(LocalDateTime.now());
        sessionRepository.save(session);
    }

    private GroupCallParticipant getOrCreateParticipant(GroupCallSession session, User user) {
        return participantRepository.findBySessionAndUser(session, user)
                .orElseGet(() -> participantRepository.save(GroupCallParticipant.builder()
                        .session(session)
                        .user(user)
                        .status("PENDING")
                        .build()));
    }

    private GroupCallSessionDto toDto(GroupCallSession session, User me) {
        List<Long> joined = participantRepository.findBySession(session).stream()
                .filter(p -> "JOINED".equals(p.getStatus()))
                .map(p -> p.getUser().getId())
                .collect(Collectors.toList());

        String myStatus = participantRepository.findBySessionAndUser(session, me)
                .map(GroupCallParticipant::getStatus)
                .orElse("NONE");

        boolean isStarter = session.getStarter().getId().equals(me.getId());
        boolean hasPendingRequest = joinRequestRepository
                .findBySessionAndUserAndStatus(session, me, "PENDING")
                .isPresent();
        int pendingCount = isStarter
                ? (int) joinRequestRepository.countBySessionAndStatus(session, "PENDING")
                : 0;

        return GroupCallSessionDto.from(
                session, myStatus, joined, isStarter, hasPendingRequest, pendingCount);
    }

    private GroupCallSession getSession(Long callId) {
        return sessionRepository.findById(callId)
                .orElseThrow(() -> new RuntimeException("Group call not found"));
    }

    private void ensureLive(GroupCallSession session) {
        if (!LIVE_STATUSES.contains(session.getStatus())) {
            throw new RuntimeException("This call has ended");
        }
    }

    private void ensureMember(SocialGroup group, User user) {
        if (!socialGroupMemberRepository.existsByGroupAndUser(group, user)) {
            throw new RuntimeException("Not a member of this group");
        }
    }

    private String buildIncomingMessage(String starterName, String groupName, String callType) {
        String media = "VIDEO".equals(callType) ? "video" : "voice";
        return starterName + " started a group " + media + " call in \"" + groupName + "\"";
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
