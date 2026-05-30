package com.example.demo.service;

import com.example.demo.entity.Like;
import com.example.demo.entity.Post;
import com.example.demo.entity.User;

import com.example.demo.repository.LikeRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LikeService {

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PostRepository postRepository;

    // LIKE POST

    public String likePost(
            Long postId,
            String email
    ) {

        User user = userRepository
                .findByEmail(email)

                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        )
                );

        Post post = postRepository
                .findById(postId)

                .orElseThrow(() ->
                        new RuntimeException(
                                "Post not found"
                        )
                );

        // ALREADY LIKED

        if (likeRepository
                .existsByUserAndPost(
                        user,
                        post
                )) {

            return "Post already liked";
        }

        // SAVE LIKE

        Like like = new Like();

        like.setUser(user);

        like.setPost(post);

        likeRepository.save(like);

        // UPDATE COUNT

        int count = (int)
                likeRepository
                        .countByPost(post);

        post.setLikesCount(count);

        postRepository.save(post);

        return "Post liked successfully";
    }

    // UNLIKE POST

    public String unlikePost(
            Long postId,
            String email
    ) {

        User user = userRepository
                .findByEmail(email)

                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        )
                );

        Post post = postRepository
                .findById(postId)

                .orElseThrow(() ->
                        new RuntimeException(
                                "Post not found"
                        )
                );

        // CHECK LIKE EXISTS

        if (!likeRepository
                .existsByUserAndPost(
                        user,
                        post
                )) {

            return "Post not liked yet";
        }

        // DELETE LIKE

        likeRepository
                .deleteByUserAndPost(
                        user,
                        post
                );

        // UPDATE COUNT

        int count = (int)
                likeRepository
                        .countByPost(post);

        post.setLikesCount(count);

        postRepository.save(post);

        return "Post unliked successfully";
    }

    // TOGGLE LIKE

    public boolean toggleLike(
            Long postId,
            String email
    ) {

        User user = userRepository
                .findByEmail(email)

                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        )
                );

        Post post = postRepository
                .findById(postId)

                .orElseThrow(() ->
                        new RuntimeException(
                                "Post not found"
                        )
                );

        // IF ALREADY LIKED

        if (likeRepository
                .existsByUserAndPost(
                        user,
                        post
                )) {

            likeRepository
                    .deleteByUserAndPost(
                            user,
                            post
                    );

            // UPDATE COUNT

            post.setLikesCount(

                    (int) likeRepository
                            .countByPost(post)
            );

            postRepository.save(post);

            return false;
        }

        // NEW LIKE

        Like like = new Like();

        like.setUser(user);

        like.setPost(post);

        likeRepository.save(like);

        // UPDATE COUNT

        post.setLikesCount(

                (int) likeRepository
                        .countByPost(post)
        );

        postRepository.save(post);

        return true;
    }

    // CHECK USER LIKED

    public boolean isPostLiked(
            Long postId,
            String email
    ) {

        User user = userRepository
                .findByEmail(email)

                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        )
                );

        Post post = postRepository
                .findById(postId)

                .orElseThrow(() ->
                        new RuntimeException(
                                "Post not found"
                        )
                );

        return likeRepository
                .existsByUserAndPost(
                        user,
                        post
                );
    }

    // COUNT LIKES

    public long getLikesCount(
            Long postId
    ) {

        Post post = postRepository
                .findById(postId)

                .orElseThrow(() ->
                        new RuntimeException(
                                "Post not found"
                        )
                );

        return likeRepository
                .countByPost(post);
    }
}