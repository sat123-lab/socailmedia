package com.example.demo.repository;

import com.example.demo.entity.Comment;
import com.example.demo.entity.Post;
import com.example.demo.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    // -------------------------------------------------------------------
    //  Legacy queries — used by deletes-on-cascade + admin tools.
    // -------------------------------------------------------------------

    List<Comment> findByPost(Post post);

    List<Comment> findByPostOrderByCreatedAtAsc(Post post);

    List<Comment> findByParentId(Long parentId);

    long countByPost(Post post);

    List<Comment> findByUser(User user);

    void deleteByPost(Post post);

    void deleteByUser(User user);

    // -------------------------------------------------------------------
    //  PHASE 1 — Threaded fetch
    // -------------------------------------------------------------------

    /** All top-level comments on a post, paginated. Excludes soft-deleted by default. */
    @Query("""
           SELECT c FROM Comment c
            WHERE c.post.id = :postId
              AND c.parentId IS NULL
            ORDER BY c.pinned DESC, c.createdAt ASC
           """)
    List<Comment> findTopLevel(@Param("postId") Long postId, Pageable page);

    /** "Top" sort — pinned first, then hot score, then newest. */
    @Query("""
           SELECT c FROM Comment c
            WHERE c.post.id = :postId
              AND c.parentId IS NULL
            ORDER BY c.pinned DESC, c.hotScore DESC, c.createdAt DESC
           """)
    List<Comment> findTopLevelByTop(@Param("postId") Long postId, Pageable page);

    /** "Newest" sort. */
    @Query("""
           SELECT c FROM Comment c
            WHERE c.post.id = :postId
              AND c.parentId IS NULL
            ORDER BY c.pinned DESC, c.createdAt DESC
           """)
    List<Comment> findTopLevelByNewest(@Param("postId") Long postId, Pageable page);

    /** Replies under a single parent, oldest-first to keep conversation flow. */
    @Query("""
           SELECT c FROM Comment c
            WHERE c.parentId = :parentId
            ORDER BY c.createdAt ASC
           """)
    List<Comment> findReplies(@Param("parentId") Long parentId, Pageable page);

    /** A single pinned comment on the post (if any). */
    @Query("""
           SELECT c FROM Comment c
            WHERE c.post.id = :postId AND c.pinned = true
           """)
    Optional<Comment> findPinned(@Param("postId") Long postId);

    /**
     * Bulk unpin — used when a new comment is pinned to enforce the
     * "one pinned per post" invariant atomically.
     */
    @Modifying
    @Query("""
           UPDATE Comment c SET c.pinned = false, c.pinnedAt = null, c.pinnedByUserId = null
            WHERE c.post.id = :postId AND c.pinned = true
           """)
    int clearPin(@Param("postId") Long postId);

    /** Increment / decrement reply_count atomically. */
    @Modifying
    @Query("UPDATE Comment c SET c.replyCount = c.replyCount + 1 WHERE c.id = :id")
    int incrementReplyCount(@Param("id") Long id);

    @Modifying
    @Query("""
           UPDATE Comment c SET c.replyCount = c.replyCount - 1
            WHERE c.id = :id AND c.replyCount > 0
           """)
    int decrementReplyCount(@Param("id") Long id);

    /** Recompute hot score after reaction changes. */
    @Modifying
    @Query("UPDATE Comment c SET c.hotScore = :score WHERE c.id = :id")
    int updateHotScore(@Param("id") Long id, @Param("score") double score);
}
