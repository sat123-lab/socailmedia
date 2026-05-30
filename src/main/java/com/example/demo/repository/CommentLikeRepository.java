package com.example.demo.repository;

import com.example.demo.entity.Comment;
import com.example.demo.entity.CommentLike;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentLikeRepository extends JpaRepository<CommentLike, Long> {

    long countByComment(Comment comment);

    boolean existsByCommentAndUser(Comment comment, User user);

    void deleteByComment(Comment comment);

    void deleteByCommentAndUser(Comment comment, User user);
}
