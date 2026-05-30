package com.example.demo.service;

import com.example.demo.dto.*;
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
public class SocialGroupService {

    private final SocialGroupRepository groupRepository;
    private final SocialGroupMemberRepository memberRepository;
    private final SocialGroupMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final RealtimeEventService realtimeEventService;

    private User currentUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    private SocialGroup getGroupOrThrow(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Group not found"));
    }

    private SocialGroupMember requireMembership(SocialGroup group, User user) {
        return memberRepository.findByGroupAndUser(group, user)
                .orElseThrow(() -> new RuntimeException("Not a member of this group"));
    }

    @Transactional
    public GroupSummaryDto createGroup(CreateGroupRequest request, Authentication auth) {
        User me = currentUser(auth);
        String name = request.getName() != null ? request.getName().trim() : "";
        if (name.isEmpty()) {
            throw new RuntimeException("Group name is required");
        }
        if (name.length() > 120) {
            throw new RuntimeException("Name too long");
        }

        SocialGroup g = SocialGroup.builder()
                .name(name)
                .creator(me)
                .build();
        g = groupRepository.save(g);

        memberRepository.save(SocialGroupMember.builder()
                .group(g)
                .user(me)
                .admin(true)
                .build());

        return GroupSummaryDto.builder()
                .id(g.getId())
                .name(g.getName())
                .memberCount(1)
                .iAmAdmin(true)
                .build();
    }

    public List<GroupSummaryDto> myGroups(Authentication auth) {
        User me = currentUser(auth);
        return memberRepository.findByUserIdWithGroup(me.getId()).stream()
                .map(m -> {
                    SocialGroup g = m.getGroup();
                    int count = memberRepository.findByGroup(g).size();
                    return GroupSummaryDto.builder()
                            .id(g.getId())
                            .name(g.getName())
                            .memberCount(count)
                            .iAmAdmin(m.isAdmin())
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Transactional
    public GroupDetailDto updateGroup(Long groupId, UpdateGroupRequest request, Authentication auth) {
        User me = currentUser(auth);
        SocialGroup g = getGroupOrThrow(groupId);
        SocialGroupMember myMembership = requireMembership(g, me);
        if (!myMembership.isAdmin()) {
            throw new RuntimeException("Only a group admin can rename the group");
        }
        String name = request.getName() != null ? request.getName().trim() : "";
        if (name.isEmpty()) {
            throw new RuntimeException("Group name is required");
        }
        if (name.length() > 120) {
            throw new RuntimeException("Name too long");
        }
        g.setName(name);
        groupRepository.save(g);
        return getGroup(groupId, auth);
    }

    public GroupDetailDto getGroup(Long groupId, Authentication auth) {
        User me = currentUser(auth);
        SocialGroup g = getGroupOrThrow(groupId);
        SocialGroupMember myMembership = requireMembership(g, me);

        List<GroupMemberDto> members = memberRepository.findByGroup(g).stream()
                .map(m -> GroupMemberDto.builder()
                        .userId(m.getUser().getId())
                        .name(m.getUser().getName())
                        .admin(m.isAdmin())
                        .build())
                .collect(Collectors.toList());

        return GroupDetailDto.builder()
                .id(g.getId())
                .name(g.getName())
                .creatorId(g.getCreator().getId())
                .iAmAdmin(myMembership.isAdmin())
                .members(members)
                .build();
    }

    @Transactional
    public GroupDetailDto addMember(Long groupId, AddGroupMemberRequest body, Authentication auth) {
        User me = currentUser(auth);
        SocialGroup g = getGroupOrThrow(groupId);
        SocialGroupMember myMembership = requireMembership(g, me);
        if (!myMembership.isAdmin()) {
            throw new RuntimeException("Only a group admin can add members");
        }
        Long newUserId = body.getUserId();
        if (newUserId == null) {
            throw new RuntimeException("userId is required");
        }
        User newUser = userRepository.findById(newUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (memberRepository.existsByGroupAndUser(g, newUser)) {
            return getGroup(groupId, auth);
        }

        memberRepository.save(SocialGroupMember.builder()
                .group(g)
                .user(newUser)
                .admin(false)
                .build());

        for (SocialGroupMember m : memberRepository.findByGroup(g)) {
            User r = m.getUser();
            if (r.getId().equals(me.getId())) {
                continue;
            }
            String msg = r.getId().equals(newUser.getId())
                    ? me.getName() + " added you to \"" + g.getName() + "\""
                    : me.getName() + " added " + newUser.getName() + " to \"" + g.getName() + "\"";
            notificationRepository.save(Notification.builder()
                    .receiver(r)
                    .sender(me)
                    .type("GROUP_MEMBER_ADDED")
                    .message(msg)
                    .relatedId(g.getId())
                    .read(false)
                    .build());
        }

        return getGroup(groupId, auth);
    }

    public List<GroupMessageDto> getMessages(Long groupId, Authentication auth) {
        User me = currentUser(auth);
        SocialGroup g = getGroupOrThrow(groupId);
        requireMembership(g, me);

        return messageRepository.findByGroupOrderByCreatedAtAsc(g).stream()
                .map(msg -> GroupMessageDto.builder()
                        .id(msg.getId())
                        .content(msg.getContent())
                        .senderId(msg.getSender().getId())
                        .senderName(msg.getSender().getName())
                        .createdAt(msg.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional
    public GroupMessageDto sendMessage(Long groupId, SendGroupMessageRequest body, Authentication auth) {
        User me = currentUser(auth);
        SocialGroup g = getGroupOrThrow(groupId);
        requireMembership(g, me);

        String content = body.getContent() != null ? body.getContent().trim() : "";
        if (content.isEmpty()) {
            throw new RuntimeException("Message cannot be empty");
        }
        if (content.length() > 2000) {
            throw new RuntimeException("Message too long");
        }

        SocialGroupMessage saved = messageRepository.save(SocialGroupMessage.builder()
                .group(g)
                .sender(me)
                .content(content)
                .build());

        for (SocialGroupMember m : memberRepository.findByGroup(g)) {
            User r = m.getUser();
            if (r.getId().equals(me.getId())) {
                continue;
            }
            notificationRepository.save(Notification.builder()
                    .receiver(r)
                    .sender(me)
                    .type("GROUP_MESSAGE")
                    .message(me.getName() + " in \"" + g.getName() + "\": " + truncate(content, 80))
                    .relatedId(g.getId())
                    .read(false)
                    .build());
        }

        // Realtime fan-out — admin Private-Chats Monitor (REDACTED metadata only).
        try {
            java.util.Map<String, Object> meta = new java.util.HashMap<>();
            meta.put("id", saved.getId());
            meta.put("kind", "GROUP");
            meta.put("senderId", me.getId());
            meta.put("senderName", me.getName());
            meta.put("receiverId", g.getId());
            meta.put("receiverName", "Group · " + g.getName());
            meta.put("encrypted", saved.isEncrypted());
            meta.put("encryptionAlgo", saved.getEncryptionAlgo());
            meta.put("encryptedLength", content.length());
            meta.put("createdAt", String.valueOf(saved.getCreatedAt()));
            realtimeEventService.toPrivateChatsAdmin(meta);
        } catch (Exception ignored) { /* never break sends on telemetry */ }

        return GroupMessageDto.builder()
                .id(saved.getId())
                .content(saved.getContent())
                .senderId(me.getId())
                .senderName(me.getName())
                .createdAt(saved.getCreatedAt())
                .build();
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }
}
