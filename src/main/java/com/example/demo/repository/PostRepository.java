package com.example.demo.repository;

import com.example.demo.entity.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository
        extends JpaRepository<Post, Long> {

    // FEED POSTS

    List<Post>
    findAllByOrderByCreatedAtDesc();

    List<Post>
    findAllByOrderByCreatedAtDesc(Pageable pageable);

    // USER POSTS

    List<Post>
    findByUserIdOrderByCreatedAtDesc(
            Long userId
    );

    long countByUser_Id(Long userId);

    List<Post>
    findByVideoUrlIsNotNullOrderByCreatedAtDesc();

    List<Post>
    findByCaptionContainingIgnoreCaseOrderByCreatedAtDesc(
            String caption
    );

    List<Post>
    findByVideoUrlIsNotNullAndCaptionContainingIgnoreCaseOrderByCreatedAtDesc(
            String caption
    );
}