package com.example.demo.service;

import com.example.demo.dto.AdminCommentDto;
import com.example.demo.dto.AdminDashboardDto;
import com.example.demo.dto.AdminPostDto;
import com.example.demo.dto.AdminStatsDto;
import com.example.demo.dto.AdminStoryDto;
import com.example.demo.dto.AdminUserDto;
import com.example.demo.dto.ChatRoomDto;
import com.example.demo.entity.ChatRoom;
import com.example.demo.entity.ChatRoomMember;
import com.example.demo.entity.Comment;
import com.example.demo.entity.Post;
import com.example.demo.entity.Story;
import com.example.demo.entity.User;
import com.example.demo.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final LikeRepository likeRepository;
    private final FollowRepository followRepository;
    private final FollowRequestRepository followRequestRepository;
    private final NotificationRepository notificationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final StoryRepository storyRepository;
    private final SavedPostRepository savedPostRepository;
    private final UserBlockRepository userBlockRepository;

    private User requireAdmin(Authentication auth) {
        User user = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!user.isAdmin()) {
            throw new RuntimeException("Admin access required");
        }
        return user;
    }

    public void ensureAdmin(Authentication auth) {
        requireAdmin(auth);
    }

    public AdminStatsDto stats(Authentication auth) {
        requireAdmin(auth);
        long active = chatRoomRepository.findAll().stream().filter(ChatRoom::isActive).count();
        return AdminStatsDto.builder()
                .users(userRepository.count())
                .posts(postRepository.count())
                .comments(commentRepository.count())
                .likes(likeRepository.count())
                .follows(followRepository.count())
                .notifications(notificationRepository.count())
                .messages(chatMessageRepository.count())
                .stories(storyRepository.count())
                .chatRooms(chatRoomRepository.count())
                .activeChatRooms(active)
                .savedPosts(savedPostRepository.count())
                .blockedAccounts(userBlockRepository.count())
                .build();
    }

    public List<AdminUserDto> listUsers(Authentication auth) {
        requireAdmin(auth);
        return userRepository.findAll().stream()
                .map(AdminUserDto::from)
                .collect(Collectors.toList());
    }

    public List<AdminPostDto> listPosts(Authentication auth) {
        requireAdmin(auth);
        return postRepository.findAll().stream()
                .map(AdminPostDto::from)
                .collect(Collectors.toList());
    }

    public List<ChatRoomDto> listRooms(Authentication auth) {
        requireAdmin(auth);
        return chatRoomRepository.findAll().stream()
                .map(room -> {
                    List<ChatRoomMember> members = chatRoomMemberRepository.findByRoom(room);
                    List<Long> ids = members.stream()
                            .map(m -> m.getUser().getId())
                            .collect(Collectors.toList());
                    return ChatRoomDto.from(room, members.size(), ids);
                })
                .collect(Collectors.toList());
    }

    public void deleteUser(Long userId, Authentication auth) {
        requireAdmin(auth);
        userRepository.findById(userId).ifPresent(this::purgeUser);
    }

    public int cleanupTestAccounts(Authentication auth) {
        requireAdmin(auth);
        int removed = 0;
        List<String> testEmails = List.of(
                "followtest99@test.com",
                "followtest2@test.com",
                "testfollow99@test.com"
        );
        for (String email : testEmails) {
            var user = userRepository.findByEmail(email);
            if (user.isPresent()) {
                purgeUser(user.get());
                removed++;
            }
        }
        return removed;
    }

    private void purgeUser(User user) {
        followRequestRepository.deleteAllForUser(user);
        followRepository.findByFollower(user).forEach(followRepository::delete);
        followRepository.findByFollowing(user).forEach(followRepository::delete);
        userRepository.delete(user);
    }

    public void deletePost(Long postId, Authentication auth) {
        requireAdmin(auth);
        if (postRepository.existsById(postId)) {
            postRepository.deleteById(postId);
        }
    }

    public void setUserAdmin(Long userId, boolean admin, Authentication auth) {
        requireAdmin(auth);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setAdmin(admin);
        userRepository.save(user);
    }

    public java.util.Map<String, Object> setUserVerified(Long userId, boolean verified,
                                                          Authentication auth) {
        requireAdmin(auth);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setVerified(verified);
        userRepository.save(user);
        return java.util.Map.of("id", userId, "verified", verified);
    }

    /* ====================== Stories ====================== */

    public List<AdminStoryDto> listStories(Authentication auth) {
        requireAdmin(auth);
        return storyRepository.findAll().stream()
                .sorted(Comparator.comparing(
                        Story::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(AdminStoryDto::from)
                .collect(Collectors.toList());
    }

    public void deleteStory(Long storyId, Authentication auth) {
        requireAdmin(auth);
        if (storyRepository.existsById(storyId)) {
            storyRepository.deleteById(storyId);
        }
    }

    /* ====================== Comments ====================== */

    public List<AdminCommentDto> listComments(Authentication auth) {
        requireAdmin(auth);
        return commentRepository.findAll().stream()
                .sorted(Comparator.comparing(
                        Comment::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(AdminCommentDto::from)
                .collect(Collectors.toList());
    }

    public void deleteComment(Long commentId, Authentication auth) {
        requireAdmin(auth);
        if (commentRepository.existsById(commentId)) {
            commentRepository.deleteById(commentId);
        }
    }

    /* ====================== Dashboard ====================== */

    public AdminDashboardDto dashboard(Authentication auth) {
        requireAdmin(auth);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime dayAgo = now.minusHours(24);
        LocalDateTime fourteenDaysAgo = now.minusDays(13).toLocalDate().atStartOfDay();

        List<Post> allPosts = postRepository.findAll();
        List<Comment> allComments = commentRepository.findAll();
        List<User> allUsers = userRepository.findAll();

        long postsToday = allPosts.stream()
                .filter(p -> p.getCreatedAt() != null && p.getCreatedAt().isAfter(dayAgo))
                .count();
        long commentsToday = allComments.stream()
                .filter(c -> c.getCreatedAt() != null && c.getCreatedAt().isAfter(dayAgo))
                .count();
        long activeStories = storyRepository.findAll().stream()
                .filter(s -> s.getExpiresAt() == null || s.getExpiresAt().isAfter(now))
                .count();

        AdminStatsDto stats = stats(auth);

        List<AdminDashboardDto.DailyMetric> postsTrend =
                buildTrend(allPosts.stream().map(Post::getCreatedAt).toList(), fourteenDaysAgo);
        List<AdminDashboardDto.DailyMetric> commentsTrend =
                buildTrend(allComments.stream().map(Comment::getCreatedAt).toList(), fourteenDaysAgo);

        List<AdminUserDto> topUsers = allUsers.stream()
                .sorted(Comparator.comparingInt(User::getFollowersCount).reversed())
                .limit(10)
                .map(AdminUserDto::from)
                .collect(Collectors.toList());

        List<AdminPostDto> recentPosts = allPosts.stream()
                .sorted(Comparator.comparing(
                        Post::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(AdminPostDto::from)
                .collect(Collectors.toList());

        List<AdminCommentDto> recentComments = allComments.stream()
                .sorted(Comparator.comparing(
                        Comment::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(AdminCommentDto::from)
                .collect(Collectors.toList());

        return AdminDashboardDto.builder()
                .stats(stats)
                .postsTrend(postsTrend)
                .commentsTrend(commentsTrend)
                .topUsers(topUsers)
                .recentPosts(recentPosts)
                .recentComments(recentComments)
                .postsToday(postsToday)
                .commentsToday(commentsToday)
                .activeStories(activeStories)
                .build();
    }

    private List<AdminDashboardDto.DailyMetric> buildTrend(
            List<LocalDateTime> timestamps,
            LocalDateTime since
    ) {
        Map<LocalDate, Long> grouped = new HashMap<>();
        for (LocalDateTime ts : timestamps) {
            if (ts == null || ts.isBefore(since)) continue;
            LocalDate day = ts.toLocalDate();
            grouped.merge(day, 1L, Long::sum);
        }

        List<AdminDashboardDto.DailyMetric> series = new ArrayList<>();
        LocalDate startDay = since.toLocalDate();
        LocalDate today = LocalDate.now();
        LocalDate cursor = startDay;
        while (!cursor.isAfter(today)) {
            series.add(AdminDashboardDto.DailyMetric.builder()
                    .date(cursor.toString())
                    .count(grouped.getOrDefault(cursor, 0L))
                    .build());
            cursor = cursor.plusDays(1);
        }
        return series;
    }
}
