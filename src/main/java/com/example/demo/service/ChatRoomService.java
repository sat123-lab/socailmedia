package com.example.demo.service;

import com.example.demo.dto.ChatRoomDto;
import com.example.demo.dto.ChatRoomMessageDto;
import com.example.demo.entity.ChatRoom;
import com.example.demo.entity.ChatRoomMember;
import com.example.demo.entity.ChatRoomMessage;
import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final ChatRoomMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final RealtimeEventService realtimeEventService;

    private User currentUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public ChatRoomDto createRoom(
            String name,
            String emoji,
            List<Long> memberIds,
            Authentication auth
    ) {
        User creator = currentUser(auth);

        ChatRoom room = ChatRoom.builder()
                .name(name.trim())
                .emoji(emoji != null ? emoji : "💬")
                .creator(creator)
                .active(true)
                .build();

        final ChatRoom savedRoom = chatRoomRepository.save(room);

        addMember(savedRoom, creator, false);

        if (memberIds != null) {
            for (Long memberId : memberIds) {
                if (!memberId.equals(creator.getId())) {
                    userRepository.findById(memberId).ifPresent(u ->
                            addMember(savedRoom, u, true));
                }
            }
        }

        return toDto(savedRoom);
    }

    private void addMember(ChatRoom room, User user, boolean notifyOthers) {
        if (memberRepository.existsByRoomAndUser(room, user)) {
            return;
        }

        memberRepository.save(ChatRoomMember.builder()
                .room(room)
                .user(user)
                .build());

        if (notifyOthers) {
            notifyRoomMembers(room, user,
                    user.getName() + " joined \"" + room.getName() + "\"",
                    "ROOM_JOIN");
        }
    }

    private void notifyRoomMembers(ChatRoom room, User actor, String message, String type) {
        List<ChatRoomMember> members = memberRepository.findByRoom(room);
        for (ChatRoomMember member : members) {
            User receiver = member.getUser();
            if (receiver.getId().equals(actor.getId())) {
                continue;
            }
            Notification notification = Notification.builder()
                    .receiver(receiver)
                    .sender(actor)
                    .type(type)
                    .message(message)
                    .relatedId(room.getId())
                    .read(false)
                    .build();
            notificationRepository.save(notification);
        }
    }

    public List<ChatRoomDto> myRooms(Authentication auth) {
        User user = currentUser(auth);
        return chatRoomRepository.findRoomsForUser(user.getId())
                .stream()
                .sorted((a, b) -> {
                    if (a.isActive() != b.isActive()) {
                        return a.isActive() ? -1 : 1;
                    }
                    if (a.getCreatedAt() == null || b.getCreatedAt() == null) {
                        return 0;
                    }
                    return b.getCreatedAt().compareTo(a.getCreatedAt());
                })
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    public ChatRoomDto getRoom(Long roomId, Authentication auth) {
        ChatRoom room = getRoomEntity(roomId);
        ensureMember(room, currentUser(auth));
        return toDto(room);
    }

    public List<ChatRoomMessageDto> getMessages(Long roomId, Authentication auth) {
        ChatRoom room = getRoomEntity(roomId);
        ensureMember(room, currentUser(auth));
        if (!room.isActive()) {
            throw new RuntimeException("This chat room has ended");
        }
        return messageRepository.findByRoomOrderByCreatedAtAsc(room)
                .stream()
                .map(ChatRoomMessageDto::from)
                .collect(Collectors.toList());
    }

    public ChatRoomMessageDto sendMessage(Long roomId, String content, Authentication auth) {
        User user = currentUser(auth);
        ChatRoom room = getRoomEntity(roomId);
        ensureMember(room, user);
        if (!room.isActive()) {
            throw new RuntimeException("This chat room has ended");
        }

        ChatRoomMessage message = ChatRoomMessage.builder()
                .room(room)
                .sender(user)
                .content(content.trim())
                .build();

        ChatRoomMessage saved = messageRepository.save(message);

        // Realtime fan-out — admin Private-Chats Monitor (REDACTED metadata only).
        try {
            java.util.Map<String, Object> meta = new java.util.HashMap<>();
            meta.put("id", saved.getId());
            meta.put("kind", "ROOM");
            meta.put("senderId", user.getId());
            meta.put("senderName", user.getName());
            meta.put("receiverId", room.getId());
            meta.put("receiverName", "Room · " + room.getName());
            meta.put("encrypted", saved.isEncrypted());
            meta.put("encryptionAlgo", saved.getEncryptionAlgo());
            meta.put("encryptedLength", saved.getContent() == null ? 0 : saved.getContent().length());
            meta.put("createdAt", String.valueOf(saved.getCreatedAt()));
            realtimeEventService.toPrivateChatsAdmin(meta);
        } catch (Exception ignored) { /* never break sends on telemetry */ }

        return ChatRoomMessageDto.from(saved);
    }

    public ChatRoomDto joinByInviteCode(String inviteCode, Authentication auth) {
        User user = currentUser(auth);
        String code = inviteCode != null ? inviteCode.trim().toUpperCase() : "";

        ChatRoom room = chatRoomRepository.findByInviteCode(code)
                .orElseThrow(() -> new RuntimeException("Invalid invite link or code"));

        if (!room.isActive()) {
            throw new RuntimeException("This chat room has ended");
        }

        boolean alreadyMember = memberRepository.existsByRoomAndUser(room, user);
        addMember(room, user, !alreadyMember);

        return toDto(room);
    }

    public ChatRoomDto endRoom(Long roomId, Authentication auth) {
        User user = currentUser(auth);
        ChatRoom room = getRoomEntity(roomId);

        if (!room.getCreator().getId().equals(user.getId())) {
            throw new RuntimeException("Only the creator can end the room");
        }

        room.setActive(false);
        chatRoomRepository.save(room);

        notifyRoomMembers(room, user,
                user.getName() + " ended the room \"" + room.getName() + "\"",
                "ROOM_ENDED");

        return toDto(room);
    }

    public void leaveRoom(Long roomId, Authentication auth) {
        User user = currentUser(auth);
        ChatRoom room = getRoomEntity(roomId);

        ChatRoomMember membership = memberRepository.findByRoomAndUser(room, user)
                .orElseThrow(() -> new RuntimeException("Not a member"));

        memberRepository.delete(membership);

        notifyRoomMembers(room, user,
                user.getName() + " left \"" + room.getName() + "\"",
                "ROOM_JOIN");
    }

    public ChatRoomDto addMember(Long roomId, Long userId, Authentication auth) {
        ChatRoom room = getRoomEntity(roomId);
        User requester = currentUser(auth);

        if (!room.getCreator().getId().equals(requester.getId())) {
            throw new RuntimeException("Only creator can add members");
        }

        User newMember = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        addMember(room, newMember, true);
        return toDto(room);
    }

    private ChatRoom getRoomEntity(Long roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
    }

    private void ensureMember(ChatRoom room, User user) {
        if (!memberRepository.existsByRoomAndUser(room, user)) {
            throw new RuntimeException("Not a member of this room");
        }
    }

    private ChatRoomDto toDto(ChatRoom room) {
        List<ChatRoomMember> members = memberRepository.findByRoom(room);
        List<Long> ids = members.stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toList());
        return ChatRoomDto.from(room, members.size(), ids);
    }
}
