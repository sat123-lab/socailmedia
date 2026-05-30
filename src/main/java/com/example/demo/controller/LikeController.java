package com.example.demo.controller;

import com.example.demo.entity.Like;
import com.example.demo.entity.Notification;
import com.example.demo.entity.Post;
import com.example.demo.entity.User;

import com.example.demo.repository.LikeRepository;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/likes")
@RequiredArgsConstructor
@CrossOrigin("*")
public class LikeController {

    private final LikeRepository likeRepository;

    private final UserRepository userRepository;

    private final PostRepository postRepository;

    private final NotificationRepository notificationRepository;

    // LIKE POST

    @PostMapping("/{postId}")
    public Post likePost(

            @PathVariable Long postId,

            Authentication authentication
    ) {

        String email =
                authentication.getName();

        User user =
                userRepository.findByEmail(email)

                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        Post post =
                postRepository.findById(postId)

                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Post not found"
                                )
                        );

        boolean alreadyLiked =
                likeRepository.existsByUserAndPost(
                        user,
                        post
                );

        // ALREADY LIKED

        if (alreadyLiked) {

            return post;
        }

        // CREATE LIKE

        Like like = new Like();

        like.setUser(user);

        like.setPost(post);

        likeRepository.save(like);

        // UPDATE COUNT

        int totalLikes =

                (int) likeRepository
                        .countByPost(post);

        post.setLikesCount(totalLikes);

        Post updatedPost =
                postRepository.save(post);

        // CREATE NOTIFICATION

        if (!post.getUser()
                .getId()
                .equals(user.getId())) {

            Notification notification =
                    Notification.builder()

                            .receiver(post.getUser())

                            .sender(user)

                            .type("LIKE")

                            .message(
                                    user.getName()
                                            + " liked your post"
                            )

                            .postId(post.getId())

                            .read(false)

                            .build();

            notificationRepository
                    .save(notification);
        }

        return updatedPost;
    }

    // UNLIKE POST

    @DeleteMapping("/{postId}")
    public Post unlikePost(

            @PathVariable Long postId,

            Authentication authentication
    ) {

        String email =
                authentication.getName();

        User user =
                userRepository.findByEmail(email)

                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        Post post =
                postRepository.findById(postId)

                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Post not found"
                                )
                        );

        boolean liked =
                likeRepository.existsByUserAndPost(
                        user,
                        post
                );

        if (liked) {

            likeRepository.deleteByUserAndPost(
                    user,
                    post
            );
        }

        // UPDATE COUNT

        int totalLikes =

                (int) likeRepository
                        .countByPost(post);

        post.setLikesCount(totalLikes);

        return postRepository.save(post);
    }

    // CHECK USER LIKED

    @GetMapping("/check/{postId}")
    public boolean isLiked(

            @PathVariable Long postId,

            Authentication authentication
    ) {

        String email =
                authentication.getName();

        User user =
                userRepository.findByEmail(email)

                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        Post post =
                postRepository.findById(postId)

                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Post not found"
                                )
                        );

        return likeRepository.existsByUserAndPost(
                user,
                post
        );
    }

    // GET LIKES COUNT

    @GetMapping("/count/{postId}")
    public long getLikesCount(

            @PathVariable Long postId
    ) {

        Post post =
                postRepository.findById(postId)

                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Post not found"
                                )
                        );

        return likeRepository.countByPost(post);
    }
}