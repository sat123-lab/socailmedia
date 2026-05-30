package com.example.demo.service;

import com.example.demo.dto.StoryGroupDto;
import com.example.demo.dto.StoryItemDto;
import com.example.demo.entity.Follow;
import com.example.demo.entity.Story;
import com.example.demo.entity.StoryView;
import com.example.demo.entity.User;
import com.example.demo.entity.Notification;
import com.example.demo.repository.FollowRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.StoryRepository;
import com.example.demo.repository.StoryViewRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StoryService {

    private final StoryRepository storyRepository;
    private final StoryViewRepository storyViewRepository;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;
    private final NotificationRepository notificationRepository;
    private final FileService fileService;

    public Story uploadStory(
            String email,
            MultipartFile media
    ) throws Exception {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (media == null || media.isEmpty()) {
            throw new RuntimeException("Media file required");
        }

        String mediaUrl = fileService.uploadFile(media);
        String contentType = media.getContentType() != null
                ? media.getContentType()
                : "";
        String mediaType = contentType.startsWith("video")
                ? "video"
                : "image";

        Story story = Story.builder()
                .user(user)
                .mediaUrl(mediaUrl)
                .mediaType(mediaType)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        Story saved = storyRepository.save(story);

        List<Follow> followers =
                followRepository.findByFollowing(user);
        for (Follow f : followers) {
            User receiver = f.getFollower();
            if (receiver.getId().equals(user.getId())) {
                continue;
            }
            notificationRepository.save(Notification.builder()
                    .receiver(receiver)
                    .sender(user)
                    .type("STORY")
                    .message(user.getName() + " added a new status")
                    .relatedId(user.getId())
                    .read(false)
                    .build());
        }

        return saved;
    }

    public List<StoryGroupDto> getStoryFeed(String email) {

        User currentUser = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        LocalDateTime now = LocalDateTime.now();

        Set<User> users = new LinkedHashSet<>();
        users.add(currentUser);

        List<Follow> following =
                followRepository.findByFollower(currentUser);

        for (Follow follow : following) {
            users.add(follow.getFollowing());
        }

        List<StoryGroupDto> groups = new ArrayList<>();

        for (User user : users) {

            List<Story> activeStories =
                    storyRepository
                            .findByUserAndExpiresAtAfterOrderByCreatedAtAsc(
                                    user,
                                    now
                            );

            if (activeStories.isEmpty()) {
                continue;
            }

            List<StoryItemDto> items = activeStories.stream()
                    .map(StoryItemDto::from)
                    .toList();

            boolean hasUnseen = activeStories.stream()
                    .anyMatch(story ->
                            !storyViewRepository.existsByStoryIdAndViewerId(
                                    story.getId(),
                                    currentUser.getId()
                            )
                    );

            groups.add(
                    StoryGroupDto.builder()
                            .userId(user.getId())
                            .userName(user.getName())
                            .profileImage(user.getProfileImage())
                            .isOwn(user.getId().equals(currentUser.getId()))
                            .hasUnseen(hasUnseen)
                            .stories(items)
                            .build()
            );
        }

        return groups;
    }

    public void markStoriesViewed(String email, List<Long> storyIds) {

        User viewer = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        for (Long storyId : storyIds) {
            if (storyId == null) {
                continue;
            }
            if (!storyViewRepository.existsByStoryIdAndViewerId(
                    storyId,
                    viewer.getId()
            )) {
                storyViewRepository.save(
                        StoryView.builder()
                                .storyId(storyId)
                                .viewerId(viewer.getId())
                                .build()
                );
            }
        }
    }

    public boolean hasActiveStory(Long userId) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return storyRepository.existsByUserAndExpiresAtAfter(
                user,
                LocalDateTime.now()
        );
    }

    public void deleteStory(String email, Long storyId) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Story story = storyRepository.findById(storyId)
                .orElseThrow(() -> new RuntimeException("Story not found"));

        if (!story.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Not allowed to delete this story");
        }

        storyViewRepository.deleteByStoryId(storyId);

        if (story.getMediaUrl() != null && !story.getMediaUrl().isBlank()) {
            try {
                fileService.deleteFile(story.getMediaUrl());
            } catch (Exception ignored) {
                // Story row is removed even if file cleanup fails.
            }
        }

        storyRepository.delete(story);
    }
}
