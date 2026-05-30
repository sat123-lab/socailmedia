package com.example.demo.service;

import com.example.demo.dto.BlockedUserDto;
import com.example.demo.entity.User;
import com.example.demo.entity.UserBlock;
import com.example.demo.repository.FollowRepository;
import com.example.demo.repository.FollowRequestRepository;
import com.example.demo.repository.UserBlockRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlockService {

    private final UserBlockRepository userBlockRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final FollowRequestRepository followRequestRepository;
    private final FollowCountService followCountService;

    private User currentUser(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @Transactional
    public String blockUser(Long targetUserId, Authentication auth) {
        User blocker = currentUser(auth);
        if (blocker.getId().equals(targetUserId)) {
            throw new RuntimeException("You cannot block yourself");
        }

        User blocked = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (userBlockRepository.existsByBlockerAndBlocked(blocker, blocked)) {
            return "Already blocked";
        }

        removeFollowRelation(blocker, blocked);
        removeFollowRelation(blocked, blocker);
        removePendingRequests(blocker, blocked);
        removePendingRequests(blocked, blocker);

        userBlockRepository.save(UserBlock.builder()
                .blocker(blocker)
                .blocked(blocked)
                .build());

        return "User blocked";
    }

    @Transactional
    public String unblockUser(Long targetUserId, Authentication auth) {
        User blocker = currentUser(auth);
        User blocked = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        userBlockRepository.deleteByBlockerAndBlocked(blocker, blocked);
        return "User unblocked";
    }

    public List<BlockedUserDto> getBlockedUsers(Authentication auth) {
        User blocker = currentUser(auth);
        return userBlockRepository.findByBlockerOrderByCreatedAtDesc(blocker)
                .stream()
                .map(UserBlock::getBlocked)
                .map(BlockedUserDto::from)
                .collect(Collectors.toList());
    }

    public boolean isBlocked(Long targetUserId, Authentication auth) {
        User blocker = currentUser(auth);
        User blocked = userRepository.findById(targetUserId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return userBlockRepository.existsByBlockerAndBlocked(blocker, blocked);
    }

    private void removeFollowRelation(User follower, User following) {
        if (!followRepository.existsByFollowerAndFollowing(follower, following)) {
            return;
        }
        followRepository.deleteByFollowerAndFollowing(follower, following);
        followCountService.syncCountsAfterFollowChange(follower, following);
    }

    private void removePendingRequests(User a, User b) {
        followRequestRepository
                .findByRequesterAndTargetAndStatus(a, b, "PENDING")
                .ifPresent(followRequestRepository::delete);
        followRequestRepository
                .findByRequesterAndTargetAndStatus(b, a, "PENDING")
                .ifPresent(followRequestRepository::delete);
    }
}
