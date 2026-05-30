package com.example.demo.repository;

import com.example.demo.entity.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    Optional<MessageReaction> findByMessageIdAndUserIdAndEmoji(
            Long messageId, Long userId, String emoji);

    List<MessageReaction> findByMessageId(Long messageId);

    /** Pulls reactions for an entire batch of messages — used when
     *  hydrating a page of chat history so we never N+1. */
    @Query("SELECT r FROM MessageReaction r WHERE r.messageId IN :ids")
    List<MessageReaction> findByMessageIds(@Param("ids") Collection<Long> ids);

    /** Aggregates {@code emoji -> count} for one message. */
    @Query("""
           SELECT r.emoji, COUNT(r.id) FROM MessageReaction r
            WHERE r.messageId = :messageId GROUP BY r.emoji
           """)
    List<Object[]> countsByEmoji(@Param("messageId") Long messageId);

    @Modifying
    @Transactional
    @Query("DELETE FROM MessageReaction r WHERE r.messageId = :messageId")
    int deleteAllForMessage(@Param("messageId") Long messageId);
}
