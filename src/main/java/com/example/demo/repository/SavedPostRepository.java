package com.example.demo.repository;

import com.example.demo.entity.Post;
import com.example.demo.entity.SavedPost;
import com.example.demo.entity.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedPostRepository
        extends JpaRepository<SavedPost, Long> {

    // CHECK SAVED

    Optional<SavedPost> findByUserAndPost(
            User user,
            Post post
    );

    // GET SAVED POSTS

    List<SavedPost> findByUser(User user);

    // REMOVE SAVED

    @Transactional
    void deleteByUserAndPost(
            User user,
            Post post
    );

    // CHECK EXISTS

    boolean existsByUserAndPost(
            User user,
            Post post
    );
}