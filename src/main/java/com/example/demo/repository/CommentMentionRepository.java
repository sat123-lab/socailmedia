package com.example.demo.repository;

import com.example.demo.entity.CommentMention;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CommentMentionRepository
        extends JpaRepository<CommentMention, Long> {

    List<CommentMention> findByCommentId(Long commentId);

    /** Batch fetch — backs the inline-highlight payload on the list endpoint. */
    @Query("SELECT m FROM CommentMention m WHERE m.commentId IN :ids")
    List<CommentMention> findForMany(@Param("ids") List<Long> ids);

    @Modifying
    @Query("DELETE FROM CommentMention m WHERE m.commentId = :commentId")
    int deleteAllForComment(@Param("commentId") Long commentId);
}
