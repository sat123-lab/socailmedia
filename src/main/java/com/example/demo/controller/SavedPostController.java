package com.example.demo.controller;

import com.example.demo.entity.Post;
import com.example.demo.entity.SavedPost;
import com.example.demo.entity.User;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.SavedPostRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/saved")
@RequiredArgsConstructor
@CrossOrigin("*")
public class SavedPostController {

    private final SavedPostRepository savedPostRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;

    // SAVE POST

    @PostMapping("/{postId}")
    public String savePost(
            @PathVariable Long postId,
            Authentication authentication
    ) {

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        boolean alreadySaved =
                savedPostRepository.existsByUserAndPost(user, post);

        if (alreadySaved) {

            return "Post already saved";
        }

        SavedPost savedPost = SavedPost.builder()
                .user(user)
                .post(post)
                .build();

        savedPostRepository.save(savedPost);

        return "Post saved successfully";
    }

    // REMOVE SAVED POST

    @DeleteMapping("/{postId}")
    public String removeSavedPost(
            @PathVariable Long postId,
            Authentication authentication
    ) {

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        boolean exists =
                savedPostRepository.existsByUserAndPost(user, post);

        if (!exists) {

            return "Post not saved";
        }

        savedPostRepository.deleteByUserAndPost(user, post);

        return "Saved post removed successfully";
    }

    // CHECK SAVED

    @GetMapping("/check/{postId}")

    public boolean isPostSaved(
            @PathVariable Long postId,
            Authentication authentication
    ) {

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new RuntimeException("Post not found"));

        return savedPostRepository.existsByUserAndPost(user, post);
    }

    // GET SAVED POSTS

    @GetMapping
    public List<SavedPost> getSavedPosts(
            Authentication authentication
    ) {

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return savedPostRepository.findByUser(user);
    }
}