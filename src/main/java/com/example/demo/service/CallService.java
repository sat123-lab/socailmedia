package com.example.demo.service;

import com.example.demo.dto.CallSessionDto;
import com.example.demo.entity.CallSession;
import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.repository.CallSessionRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
public class CallService {

    private final CallSessionRepository callSessionRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final MessageService messageService;

    private static final List<String> BUSY_STATUSES = List.of("RINGING", "ACTIVE");
    /** Incoming ring older than this is treated as abandoned. */
    private static final int RING_STALE_SECONDS = 20;
    /** Global ring timeout for the scheduled sweeper. */
    private static final int RING_TIMEOUT_SECONDS = 45;
    /**
     * ACTIVE calls older than this without /end are treated as ghost sessions
     * (app closed mid-call). Kept at 10 minutes — long live calls still work.
     */
    private static final int ACTIVE_GHOST_SECONDS = 600;

    private User currentUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public CallSessionDto startCall(Long receiverId, String callType, Authentication auth) {
        User caller = currentUser(auth);
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new RuntimeException("Receiver not found"));

        if (caller.getId().equals(receiverId)) {
            throw new RuntimeException("Cannot call yourself");
        }

        String type = callType != null ? callType : "VOICE";

        // Clear ghost rows *before* busy check — same request must see updates.
        endOpenSessionsBetween(caller, receiver);
        releaseStaleSessions(caller);
        releaseStaleSessions(receiver);
        callSessionRepository.flush();

        if (isUserBusy(caller) || isUserBusy(receiver)) {
            log.info("Call busy after cleanup caller={} receiver={}",
                    caller.getId(), receiver.getId());
            messageService.saveCallLog(caller, receiver, type, "BUSY", null);
            notifyUser(receiver, caller, "CALL_BUSY",
                    caller.getName() + " tried to call you (you were busy)", caller.getId());
            return CallSessionDto.builder()
                    .callerId(caller.getId())
                    .callerName(caller.getName())
                    .receiverId(receiver.getId())
                    .receiverName(receiver.getName())
                    .callType(type)
                    .status("BUSY")
                    .createdAt(LocalDateTime.now())
                    .endedAt(LocalDateTime.now())
                    .build();
        }

        CallSession session = CallSession.builder()
                .caller(caller)
                .receiver(receiver)
                .callType(type)
                .status("RINGING")
                .build();

        session = callSessionRepository.save(session);

        String mediaLabel = "VIDEO".equals(type) ? "video" : "voice";
        notifyUser(receiver, caller, "CALL",
                caller.getName() + " is calling you (" + mediaLabel + " call)", session.getId());

        return CallSessionDto.from(session);
    }

    /**
     * Lets the client clear ghost sessions when opening chat (optional).
     * Does not end a legit ACTIVE call with someone else if it started
     * within {@link #ACTIVE_ZOMBIE_SECONDS}.
     */
    @Transactional
    public void releaseStaleForCurrentUser(Authentication auth) {
        User user = currentUser(auth);
        LocalDateTime now = LocalDateTime.now();
        // Client-triggered cleanup — more aggressive than the background
        // sweeper so opening chat fixes "stuck busy" immediately.
        for (CallSession s : callSessionRepository.findActiveForUser(user)) {
            if (isStaleForClientRelease(s, now)) {
                log.info("Client release-stale: ending ACTIVE call id={}", s.getId());
                finalizeSession(s, "ENDED", now, false);
            }
        }
        releaseStaleSessions(user);
        callSessionRepository.flush();
    }

    /** Aggressive ghost detection when the user opens chat / taps call. */
    private boolean isStaleForClientRelease(CallSession session, LocalDateTime now) {
        if (!"ACTIVE".equals(session.getStatus())) return false;
        if (session.getAnsweredAt() != null) {
            return Duration.between(session.getAnsweredAt(), now).getSeconds() >= 120;
        }
        if (session.getCreatedAt() == null) return true;
        return Duration.between(session.getCreatedAt(), now).getSeconds() >= 60;
    }

    @Transactional
    public CallSessionDto acceptCall(Long callId, Authentication auth) {
        User user = currentUser(auth);
        CallSession session = getSession(callId);

        if (!session.getReceiver().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized");
        }
        if (!"RINGING".equals(session.getStatus())) {
            throw new RuntimeException("Call is not ringing");
        }

        session.setStatus("ACTIVE");
        session.setAnsweredAt(LocalDateTime.now());
        return CallSessionDto.from(callSessionRepository.save(session));
    }

    @Transactional
    public CallSessionDto declineCall(Long callId, Authentication auth) {
        User user = currentUser(auth);
        CallSession session = getSession(callId);

        if (!session.getReceiver().getId().equals(user.getId())) {
            throw new RuntimeException("Not authorized");
        }

        session.setStatus("DECLINED");
        session.setEndedAt(LocalDateTime.now());
        callSessionRepository.save(session);

        User caller = session.getCaller();
        messageService.saveCallLog(user, caller, session.getCallType(), "DECLINED", null);
        notifyUser(caller, user, "MISSED_CALL",
                user.getName() + " declined your call", user.getId());

        return CallSessionDto.from(session);
    }

    @Transactional
    public CallSessionDto endCall(Long callId, Authentication auth) {
        User user = currentUser(auth);
        CallSession session = getSession(callId);

        boolean isCaller = session.getCaller().getId().equals(user.getId());
        boolean isReceiver = session.getReceiver().getId().equals(user.getId());

        if (!isCaller && !isReceiver) {
            throw new RuntimeException("Not authorized");
        }

        if ("RINGING".equals(session.getStatus())) {
            session.setStatus("MISSED");
            session.setEndedAt(LocalDateTime.now());
            callSessionRepository.save(session);

            User caller = session.getCaller();
            User receiver = session.getReceiver();
            messageService.saveCallLog(caller, receiver, session.getCallType(), "MISSED", null);

            if (isCaller) {
                notifyUser(receiver, caller, "MISSED_CALL",
                        "Missed " + callTypeLabel(session) + " call from " + caller.getName(),
                        caller.getId());
            } else {
                notifyUser(caller, receiver, "MISSED_CALL",
                        receiver.getName() + " missed your call", receiver.getId());
            }
        } else if ("ACTIVE".equals(session.getStatus())) {
            int duration = 0;
            if (session.getAnsweredAt() != null) {
                duration = (int) Duration.between(session.getAnsweredAt(), LocalDateTime.now()).getSeconds();
            }
            session.setStatus("ENDED");
            session.setEndedAt(LocalDateTime.now());
            callSessionRepository.save(session);

            User caller = session.getCaller();
            messageService.saveCallLog(caller, session.getReceiver(), session.getCallType(), "ANSWERED", duration);
        } else if (!isTerminal(session.getStatus())) {
            session.setStatus("ENDED");
            session.setEndedAt(LocalDateTime.now());
            callSessionRepository.save(session);
        }

        return CallSessionDto.from(session);
    }

    @Scheduled(fixedRate = 15000)
    @Transactional
    public void expireRingingCalls() {
        List<CallSession> ringing = callSessionRepository.findByStatus("RINGING");
        LocalDateTime now = LocalDateTime.now();

        for (CallSession session : ringing) {
            if (session.getCreatedAt() == null) continue;
            if (Duration.between(session.getCreatedAt(), now).getSeconds() < RING_TIMEOUT_SECONDS) {
                continue;
            }
            finalizeSession(session, "MISSED", now, true);
        }
    }

    @Scheduled(fixedRate = 60000)
    @Transactional
    public void cleanupZombieActiveCalls() {
        LocalDateTime now = LocalDateTime.now();
        for (CallSession session : callSessionRepository.findByStatus("ACTIVE")) {
            if (isZombieActive(session, now)) {
                log.info("Ending zombie ACTIVE call id={}", session.getId());
                finalizeSession(session, "ENDED", now, false);
            }
        }
    }

    public List<CallSessionDto> getPendingCalls(Authentication auth) {
        User user = currentUser(auth);
        return callSessionRepository.findPendingForUser(user).stream()
                .map(CallSessionDto::from)
                .toList();
    }

    public CallSessionDto getCall(Long callId, Authentication auth) {
        User user = currentUser(auth);
        CallSession session = getSession(callId);
        boolean participant = session.getCaller().getId().equals(user.getId())
                || session.getReceiver().getId().equals(user.getId());
        if (!participant) {
            throw new RuntimeException("Not authorized");
        }
        return CallSessionDto.from(session);
    }

    private void notifyUser(User receiver, User sender, String type, String message, Long relatedId) {
        Notification notification = Notification.builder()
                .receiver(receiver)
                .sender(sender)
                .type(type)
                .message(message)
                .relatedId(relatedId)
                .read(false)
                .build();
        notificationRepository.save(notification);
    }

    private String callTypeLabel(CallSession session) {
        return "VIDEO".equals(session.getCallType()) ? "video" : "voice";
    }

    private boolean isUserBusy(User user) {
        return callSessionRepository.existsByCallerAndStatusIn(user, BUSY_STATUSES)
                || callSessionRepository.existsByReceiverAndStatusIn(user, BUSY_STATUSES);
    }

    /**
     * Force-close any in-flight 1:1 session between these two users so a
     * new call can start even if the previous one never called /end.
     */
    private void endOpenSessionsBetween(User a, User b) {
        LocalDateTime now = LocalDateTime.now();
        for (CallSession session : callSessionRepository.findOpenBetween(a, b)) {
            String next = "RINGING".equals(session.getStatus()) ? "CANCELLED" : "ENDED";
            log.info("Closing open call {} between {} and {} -> {}",
                    session.getId(), a.getId(), b.getId(), next);
            finalizeSession(session, next, now, false);
        }
    }

    private void releaseStaleSessions(User user) {
        LocalDateTime now = LocalDateTime.now();
        Set<Long> seen = new HashSet<>();

        // Abandoned outgoing rings.
        for (CallSession s : callSessionRepository.findByCallerAndStatus(user, "RINGING")) {
            if (seen.add(s.getId())) {
                finalizeSession(s, "CANCELLED", now, false);
            }
        }

        // Stale incoming rings (short timeout — not 45s).
        for (CallSession s : callSessionRepository.findByReceiverAndStatus(user, "RINGING")) {
            if (!seen.add(s.getId())) continue;
            if (s.getCreatedAt() == null
                    || Duration.between(s.getCreatedAt(), now).getSeconds() >= RING_STALE_SECONDS) {
                finalizeSession(s, "MISSED", now, true);
            }
        }

        // Ghost ACTIVE rows (app closed mid-call).
        for (CallSession s : callSessionRepository.findActiveForUser(user)) {
            if (!seen.add(s.getId())) continue;
            if (isZombieActive(s, now)) {
                finalizeSession(s, "ENDED", now, false);
            }
        }
    }

    private boolean isZombieActive(CallSession session, LocalDateTime now) {
        if (!"ACTIVE".equals(session.getStatus())) return false;
        // ACTIVE but never answered is invalid — close quickly.
        if (session.getAnsweredAt() == null) {
            if (session.getCreatedAt() == null) return true;
            return Duration.between(session.getCreatedAt(), now).getSeconds() >= 120;
        }
        return Duration.between(session.getAnsweredAt(), now).getSeconds()
                >= ACTIVE_GHOST_SECONDS;
    }

    private void finalizeSession(
            CallSession session,
            String status,
            LocalDateTime endedAt,
            boolean saveMissedLog
    ) {
        if (isTerminal(session.getStatus())) return;
        session.setStatus(status);
        session.setEndedAt(endedAt);
        callSessionRepository.save(session);
        if (saveMissedLog && "MISSED".equals(status)) {
            try {
                messageService.saveCallLog(
                        session.getCaller(),
                        session.getReceiver(),
                        session.getCallType(),
                        "MISSED",
                        null);
            } catch (Exception e) {
                log.warn("Call log skipped for session {}: {}", session.getId(), e.getMessage());
            }
        }
    }

    private static boolean isTerminal(String status) {
        return status != null && List.of(
                "ENDED", "DECLINED", "MISSED", "CANCELLED", "BUSY"
        ).contains(status);
    }

    private CallSession getSession(Long callId) {
        return callSessionRepository.findById(callId)
                .orElseThrow(() -> new RuntimeException("Call not found"));
    }
}
