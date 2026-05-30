package com.example.demo.repository;

import com.example.demo.entity.CommentReaction;
import com.example.demo.entity.CommentReactionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentReactionRepository
        extends JpaRepository<CommentReaction, Long> {

    Optional<CommentReaction> findByCommentIdAndUserId(Long commentId, Long userId);

    long countByCommentId(Long commentId);

    long countByCommentIdAndEmoji(Long commentId, CommentReactionType emoji);

    @Modifying
    @Query("DELETE FROM CommentReaction r WHERE r.commentId = :commentId AND r.userId = :userId")
    int deleteByCommentAndUser(@Param("commentId") Long commentId,
                                @Param("userId") Long userId);

    @Modifying
    @Query("DELETE FROM CommentReaction r WHERE r.commentId = :commentId")
    int deleteAllForComment(@Param("commentId") Long commentId);

    /**
     * Aggregate per-emoji counts for a single comment. Returns
     * {@code [emoji, count]} rows; the service builds a map for the DTO.
     */
    @Query("""
           SELECT r.emoji, COUNT(r) FROM CommentReaction r
            WHERE r.commentId = :commentId
            GROUP BY r.emoji
           """)
    List<Object[]> countsByEmoji(@Param("commentId") Long commentId);

    /**
     * Batch fetch — used by the threaded list endpoint so we don't fire
     * N+1 reaction-count queries.
     */
    @Query("""
           SELECT r.commentId, r.emoji, COUNT(r) FROM CommentReaction r
            WHERE r.commentId IN :ids
            GROUP BY r.commentId, r.emoji
           """)
    List<Object[]> countsForMany(@Param("ids") List<Long> ids);

    /** Which of these comments has the current user reacted to, and with what emoji? */
    @Query("""
           SELECT r.commentId, r.emoji FROM CommentReaction r
            WHERE r.userId = :userId AND r.commentId IN :ids
           """)
    List<Object[]> myReactions(@Param("userId") Long userId,
                                @Param("ids") List<Long> ids);
}
