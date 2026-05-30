package com.example.demo.controller;

import com.example.demo.dto.UserSummaryDto;
import com.example.demo.entity.Follow;
import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.entity.FollowRequest;
import com.example.demo.repository.FollowRepository;
import com.example.demo.repository.FollowRequestRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.FollowCountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/follow")
@RequiredArgsConstructor
@CrossOrigin("*")
public class FollowController {

    private final FollowRepository followRepository;

    private final FollowRequestRepository followRequestRepository;

    private final UserRepository userRepository;

    private final NotificationRepository notificationRepository;

    private final FollowCountService followCountService;

    // =========================
    // FOLLOW USER
    // =========================

    @Transactional
    @PostMapping("/{userId}")

    public String followUser(

            @PathVariable Long userId,

            Authentication authentication
    ) {

        String email =
                authentication.getName();

        User follower =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new RuntimeException("User not found"));

        User following =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new RuntimeException("Target user not found"));

        // SELF FOLLOW BLOCK

        if (follower.getId()
                .equals(following.getId())) {

            return "You cannot follow yourself";
        }

        // ALREADY FOLLOWING

        boolean alreadyFollowing =

                followRepository
                        .existsByFollowerAndFollowing(

                                follower,

                                following
                        );

        if (alreadyFollowing) {

            return "Already following";
        }

        // PRIVATE ACCOUNT -> SEND REQUEST

        if (following.isPrivateAccount()) {

            Optional<FollowRequest> existingRequest =
                    followRequestRepository.findByRequesterAndTarget(
                            follower,
                            following
                    );

            if (existingRequest.isPresent()) {
                FollowRequest existing = existingRequest.get();
                if ("PENDING".equals(existing.getStatus())) {
                    return "Follow request already sent";
                }
                if ("ACCEPTED".equals(existing.getStatus())) {
                    if (followRepository.existsByFollowerAndFollowing(
                            follower,
                            following
                    )) {
                        return "Already following";
                    }
                    // Accepted earlier but unfollowed — send a new request.
                }
                existing.setStatus("PENDING");
                existing.setCreatedAt(java.time.LocalDateTime.now());
                FollowRequest saved = followRequestRepository.save(existing);
                notifyFollowRequest(follower, following, saved.getId());
                return "Follow request sent";
            }

            FollowRequest saved = followRequestRepository.save(
                    FollowRequest.builder()
                            .requester(follower)
                            .target(following)
                            .status("PENDING")
                            .build()
            );

            notifyFollowRequest(follower, following, saved.getId());

            return "Follow request sent";
        }

        // PUBLIC ACCOUNT -> DIRECT FOLLOW

        createFollowRelation(follower, following);

        Notification notification =
                Notification.builder()
                        .receiver(following)
                        .sender(follower)
                        .type("FOLLOW")
                        .message(
                                displayName(follower)
                                        + " started following you"
                        )
                        .read(false)
                        .build();

        saveNotificationSafe(notification);

        return "User followed successfully";
    }

    private void saveNotificationSafe(Notification notification) {
        try {
            notificationRepository.save(notification);
        } catch (Exception ignored) {
            // Follow must succeed even if notification insert fails.
        }
    }

    private void notifyFollowRequest(
            User follower,
            User following,
            Long requestId
    ) {
        Notification notification = Notification.builder()
                .receiver(following)
                .sender(follower)
                .type("FOLLOW_REQUEST")
                .message(displayName(follower) + " requested to follow you")
                .relatedId(requestId)
                .read(false)
                .build();
        saveNotificationSafe(notification);
    }

    private static String displayName(User user) {
        if (user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        if (user.getEmail() != null && user.getEmail().contains("@")) {
            return user.getEmail().substring(0, user.getEmail().indexOf('@'));
        }
        return "Someone";
    }

    private void createFollowRelation(
            User follower,
            User following
    ) {

        Follow follow = Follow.builder()
                .follower(follower)
                .following(following)
                .build();

        followRepository.save(follow);

        followCountService.syncCountsAfterFollowChange(follower, following);
    }

    // =========================
    // UNFOLLOW USER
    // =========================

    @Transactional
    @DeleteMapping("/{userId}")

    public String unfollowUser(

            @PathVariable Long userId,

            Authentication authentication
    ) {

        String email =
                authentication.getName();

        User follower =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new RuntimeException("User not found"));

        User following =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new RuntimeException("Target user not found"));

        // CHECK EXISTS

        boolean exists =

                followRepository
                        .existsByFollowerAndFollowing(

                                follower,

                                following
                        );

        Optional<FollowRequest> pendingRequest =
                followRequestRepository.findByRequesterAndTargetAndStatus(
                        follower,
                        following,
                        "PENDING"
                );

        if (pendingRequest.isPresent()) {
            followRequestRepository.delete(pendingRequest.get());
            return "Follow request cancelled";
        }

        followRequestRepository
                .findByRequesterAndTarget(follower, following)
                .ifPresent(followRequestRepository::delete);

        if (!exists) {

            return "You are not following this user";
        }

        followRepository
                .deleteByFollowerAndFollowing(

                        follower,

                        following
                );

        followCountService.syncCountsAfterFollowChange(follower, following);

        return "User unfollowed successfully";
    }

    // =========================
    // FOLLOWERS COUNT
    // =========================

    @GetMapping("/followers/{userId}")

    public long followersCount(
            @PathVariable Long userId
    ) {

        User user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new RuntimeException("User not found"));

        int count = followCountService.countFollowers(user);
        userRepository.updateFollowersCount(userId, count);
        return count;
    }

    // =========================
    // FOLLOWING COUNT
    // =========================

    @GetMapping("/following/{userId}")

    public long followingCount(
            @PathVariable Long userId
    ) {

        User user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new RuntimeException("User not found"));

        int count = followCountService.countFollowing(user);
        userRepository.updateFollowingCount(userId, count);
        return count;
    }

    // =========================
    // FOLLOW STATUS
    // =========================

    @GetMapping("/status/{userId}")

    public String followStatus(

            @PathVariable Long userId,

            Authentication authentication
    ) {

        String email = authentication.getName();

        User follower = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        User following = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Target user not found"));

        if (followRepository.existsByFollowerAndFollowing(follower, following)) {
            return "FOLLOWING";
        }

        if (followRequestRepository.existsByRequesterAndTargetAndStatus(
                follower, following, "PENDING")) {
            return "REQUESTED";
        }

        return "NONE";
    }

    // =========================
    // ACCEPT FOLLOW REQUEST
    // =========================

    @PostMapping("/request/accept/{requestId}")

    public String acceptFollowRequest(

            @PathVariable Long requestId,

            Authentication authentication
    ) {

        String email = authentication.getName();

        User target = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        FollowRequest request = followRequestRepository
                .findByIdAndTarget(requestId, target)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        if (!"PENDING".equals(request.getStatus())) {
            return "Request already handled";
        }

        User requester = request.getRequester();

        if (!followRepository.existsByFollowerAndFollowing(requester, target)) {
            createFollowRelation(requester, target);
        }

        request.setStatus("ACCEPTED");
        followRequestRepository.save(request);

        notificationRepository
                .findByRelatedIdAndType(requestId, "FOLLOW_REQUEST")
                .forEach(notification -> {
                    notification.setType("FOLLOW");
                    notification.setMessage(
                            requester.getName() + " started following you"
                    );
                    notification.setRead(true);
                    notificationRepository.save(notification);
                });

        Notification notification = Notification.builder()
                .receiver(requester)
                .sender(target)
                .type("FOLLOW_ACCEPTED")
                .message(target.getName() + " accepted your follow request")
                .read(false)
                .build();

        notificationRepository.save(notification);

        return "Follow request accepted";
    }

    // =========================
    // REJECT FOLLOW REQUEST
    // =========================

    @PostMapping("/request/reject/{requestId}")

    public String rejectFollowRequest(

            @PathVariable Long requestId,

            Authentication authentication
    ) {

        String email = authentication.getName();

        User target = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        FollowRequest request = followRequestRepository
                .findByIdAndTarget(requestId, target)
                .orElseThrow(() -> new RuntimeException("Request not found"));

        request.setStatus("REJECTED");
        followRequestRepository.save(request);

        notificationRepository.deleteByRelatedIdAndType(
                requestId,
                "FOLLOW_REQUEST"
        );

        return "Follow request rejected";
    }

    // =========================
    // FOLLOWERS LIST
    // =========================

    @GetMapping("/followers/list/{userId}")

    @Transactional(readOnly = true)

    public List<UserSummaryDto> followersList(
            @PathVariable Long userId
    ) {

        User user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new RuntimeException("User not found"));

        return followRepository
                .findByFollowing(user)
                .stream()
                .map(Follow::getFollower)
                .filter(follower -> !FollowCountService.isTestUser(follower))
                .map(UserSummaryDto::from)
                .toList();
    }

    // =========================
    // FOLLOWING LIST
    // =========================

    @GetMapping("/following/list/{userId}")

    @Transactional(readOnly = true)

    public List<UserSummaryDto> followingList(
            @PathVariable Long userId
    ) {

        User user =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new RuntimeException("User not found"));

        return followRepository
                .findByFollower(user)
                .stream()
                .map(Follow::getFollowing)
                .filter(target -> !FollowCountService.isTestUser(target))
                .map(UserSummaryDto::from)
                .toList();
    }

    // =========================
    // REMOVE FOLLOWER (from my followers list)
    // =========================

    @Transactional
    @DeleteMapping("/remove-follower/{followerId}")

    public String removeFollower(
            @PathVariable Long followerId,
            Authentication authentication
    ) {

        String email = authentication.getName();

        User me = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!followRepository.existsByFollowerAndFollowing(follower, me)) {
            return "This user is not following you";
        }

        followRepository.deleteByFollowerAndFollowing(follower, me);

        followCountService.syncCountsAfterFollowChange(follower, me);

        followRequestRepository
                .findByRequesterAndTarget(follower, me)
                .ifPresent(followRequestRepository::delete);

        return "Follower removed";
    }

    // =========================
    // CHECK FOLLOWING
    // =========================

    @GetMapping("/check/{userId}")

    public boolean isFollowing(

            @PathVariable Long userId,

            Authentication authentication
    ) {

        String email =
                authentication.getName();

        User follower =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new RuntimeException("User not found"));

        User following =
                userRepository.findById(userId)
                        .orElseThrow(() ->
                                new RuntimeException("Target user not found"));

        return followRepository
                .existsByFollowerAndFollowing(

                        follower,

                        following
                );
    }
}