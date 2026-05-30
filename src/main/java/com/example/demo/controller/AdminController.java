package com.example.demo.controller;

import com.example.demo.dto.*;
import com.example.demo.service.AdminService;
import com.example.demo.service.AppAdService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AdminController {

    private final AdminService adminService;
    private final AppAdService appAdService;

    @GetMapping("/stats")
    public AdminStatsDto stats(Authentication authentication) {
        return adminService.stats(authentication);
    }

    @GetMapping("/users")
    public List<AdminUserDto> users(Authentication authentication) {
        return adminService.listUsers(authentication);
    }

    @GetMapping("/posts")
    public List<AdminPostDto> posts(Authentication authentication) {
        return adminService.listPosts(authentication);
    }

    @GetMapping("/chat-rooms")
    public List<ChatRoomDto> chatRooms(Authentication authentication) {
        return adminService.listRooms(authentication);
    }

    @DeleteMapping("/users/{userId}")
    public String deleteUser(
            @PathVariable Long userId,
            Authentication authentication
    ) {
        adminService.deleteUser(userId, authentication);
        return "User deleted";
    }

    @PostMapping("/cleanup-test-accounts")
    public Map<String, Object> cleanupTestAccounts(Authentication authentication) {
        int removed = adminService.cleanupTestAccounts(authentication);
        return Map.of("removed", removed);
    }

    @DeleteMapping("/posts/{postId}")
    public String deletePost(
            @PathVariable Long postId,
            Authentication authentication
    ) {
        adminService.deletePost(postId, authentication);
        return "Post deleted";
    }

    @PatchMapping("/users/{userId}/admin")
    public String toggleAdmin(
            @PathVariable Long userId,
            @RequestBody Map<String, Boolean> body,
            Authentication authentication
    ) {
        adminService.setUserAdmin(userId, Boolean.TRUE.equals(body.get("admin")), authentication);
        return "Updated";
    }

    /**
     * Grant or revoke the blue-tick verification.
     * <p>{@code POST /admin/users/{id}/verify} body: {@code {"verified": true}}</p>
     */
    @PatchMapping("/users/{userId}/verify")
    public Map<String, Object> setVerified(
            @PathVariable Long userId,
            @RequestBody Map<String, Boolean> body,
            Authentication authentication
    ) {
        boolean v = Boolean.TRUE.equals(body.get("verified"));
        return adminService.setUserVerified(userId, v, authentication);
    }

    @GetMapping("/ads")
    public List<AppAdDto> listAds(Authentication authentication) {
        return appAdService.listAll(authentication);
    }

    @PostMapping("/ads")
    public AppAdDto createAd(
            @RequestBody CreateAppAdRequest body,
            Authentication authentication
    ) {
        return appAdService.create(body, authentication);
    }

    @PatchMapping("/ads/{adId}")
    public AppAdDto updateAd(
            @PathVariable Long adId,
            @RequestBody CreateAppAdRequest body,
            Authentication authentication
    ) {
        return appAdService.update(adId, body, authentication);
    }

    @DeleteMapping("/ads/{adId}")
    public String deleteAd(@PathVariable Long adId, Authentication authentication) {
        appAdService.delete(adId, authentication);
        return "Deleted";
    }

    @GetMapping("/stories")
    public List<AdminStoryDto> stories(Authentication authentication) {
        return adminService.listStories(authentication);
    }

    @DeleteMapping("/stories/{storyId}")
    public String deleteStory(
            @PathVariable Long storyId,
            Authentication authentication
    ) {
        adminService.deleteStory(storyId, authentication);
        return "Story deleted";
    }

    @GetMapping("/comments")
    public List<AdminCommentDto> comments(Authentication authentication) {
        return adminService.listComments(authentication);
    }

    @DeleteMapping("/comments/{commentId}")
    public String deleteComment(
            @PathVariable Long commentId,
            Authentication authentication
    ) {
        adminService.deleteComment(commentId, authentication);
        return "Comment deleted";
    }

    @GetMapping("/dashboard")
    public AdminDashboardDto dashboard(Authentication authentication) {
        return adminService.dashboard(authentication);
    }
}
