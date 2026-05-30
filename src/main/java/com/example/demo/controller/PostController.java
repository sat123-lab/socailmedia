package com.example.demo.controller;

import com.example.demo.dto.PostRequest;
import com.example.demo.entity.Post;
import com.example.demo.entity.User;

import com.example.demo.repository.UserRepository;

import com.example.demo.service.PostService;

import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController

@RequestMapping("/posts")

@CrossOrigin("*")

public class PostController {

    @Autowired
    private PostService postService;

    @Autowired
    private UserRepository userRepository;

    // =========================
    // CREATE POST / REEL
    // =========================

    @PostMapping(

            value = "/create",

            consumes = {
                    "multipart/form-data"
            }
    )

    public Post createPost(

            @RequestPart("data")
            PostRequest request,

            @RequestPart(
                    required = false
            )
            MultipartFile image,

            @RequestPart(
                    required = false
            )
            MultipartFile video,

            Authentication authentication

    ) throws Exception {

        String email =
                authentication.getName();

        return postService.createPost(

                request,
                email,
                image,
                video
        );
    }

    // =========================
    // GET FEED
    // =========================

    @GetMapping("/feed")

    public List<Post> getFeed(Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        return postService.getFeed(email);
    }

    @GetMapping("/feed/page")

    public List<Post> getFeedPage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        String email = authentication != null ? authentication.getName() : null;
        return postService.getFeedPage(page, size, email);
    }

    // =========================
    // GET REELS
    // =========================

    @GetMapping("/reels")

    public List<Post> getReels(Authentication authentication) {
        String email = authentication != null ? authentication.getName() : null;
        return postService.getReels(email);
    }

    // =========================
    // SEARCH POSTS
    // =========================

    @GetMapping("/search")

    public List<Post> searchPosts(
            @RequestParam String q,
            Authentication authentication
    ) {
        String email = authentication != null ? authentication.getName() : null;
        return postService.searchPosts(q, email);
    }

    // =========================
    // SEARCH REELS
    // =========================

    @GetMapping("/search/reels")

    public List<Post> searchReels(
            @RequestParam(required = false) String q,
            Authentication authentication
    ) {
        String email = authentication != null ? authentication.getName() : null;

        if (q == null || q.isBlank()) {
            return postService.getReels(email);
        }

        return postService.searchReels(q, email);
    }

    // =========================
    // GET SINGLE POST
    // =========================

    @GetMapping("/{postId}")

    public Post getPostById(
            @PathVariable Long postId
    ) {

        return postService
                .getPostById(postId);
    }

    // =========================
    // GET POSTS OF USER
    // =========================

    @GetMapping("/user/{userId}/count")

    public long getUserPostsCount(
            @PathVariable Long userId
    ) {

        return postService.countPostsByUser(userId);
    }

    @GetMapping("/user/{userId}")

    public List<Post> getPostsByUser(
            @PathVariable Long userId,
            Authentication authentication
    ) {

        String email = authentication != null
                ? authentication.getName()
                : null;

        return postService.getPostsByUser(userId, email);
    }

    // =========================
    // GET MY POSTS
    // =========================

    @GetMapping("/my-posts")

    public List<Post> getMyPosts(
            Authentication authentication
    ) {

        String email =
                authentication.getName();

        User user =
                userRepository.findByEmail(email)
                        .orElseThrow(() ->
                                new RuntimeException("User not found"));

        return postService.getPostsByUser(
                user.getId(),
                email
        );
    }

    // =========================
    // DELETE POST
    // =========================

    @DeleteMapping("/{postId}")

    public String deletePost(

            @PathVariable Long postId,

            Authentication authentication

    ) {

        String email =
                authentication.getName();

        postService.deletePost(

                postId,
                email
        );

        return "Post deleted successfully";
    }
}