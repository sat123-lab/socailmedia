package com.example.demo.repository;

import com.example.demo.entity.Like;
import com.example.demo.entity.Post;
import com.example.demo.entity.User;

import jakarta.transaction.Transactional;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LikeRepository
        extends JpaRepository<Like, Long> {

    // FIND LIKE

    Optional<Like> findByUserAndPost(

            User user,

            Post post
    );

    // FIND USING IDS

    Optional<Like> findByUserIdAndPostId(

            Long userId,

            Long postId
    );

    // COUNT LIKES

    long countByPost(Post post);

    int countByPostId(Long postId);

    // DELETE LIKE

    @Transactional
    void deleteByUserAndPost(

            User user,

            Post post
    );

    // GET POST LIKES

    List<Like> findByPost(Post post);

    // CHECK USER LIKED

    boolean existsByUserAndPost(

            User user,

            Post post
    );

    boolean existsByUserIdAndPostId(

            Long userId,

            Long postId
    );
}