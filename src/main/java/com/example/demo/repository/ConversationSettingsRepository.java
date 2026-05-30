package com.example.demo.repository;

import com.example.demo.entity.ConversationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ConversationSettingsRepository
        extends JpaRepository<ConversationSettings, Long> {

    Optional<ConversationSettings> findByUserIdAndConversationId(
            Long userId, Long conversationId);

    @Query("""
           SELECT s FROM ConversationSettings s
            WHERE s.userId = :userId
              AND s.pinned = true
            ORDER BY s.pinOrder ASC, s.updatedAt DESC
           """)
    List<ConversationSettings> findPinned(@Param("userId") Long userId);

    @Query("""
           SELECT s FROM ConversationSettings s
            WHERE s.userId = :userId AND s.archived = true
            ORDER BY s.updatedAt DESC
           """)
    List<ConversationSettings> findArchived(@Param("userId") Long userId);

    @Query("""
           SELECT s FROM ConversationSettings s
            WHERE s.userId = :userId AND s.folderId = :folderId
           """)
    List<ConversationSettings> findInFolder(
            @Param("userId") Long userId, @Param("folderId") Long folderId);

    @Query("""
           SELECT s FROM ConversationSettings s
            WHERE s.userId = :userId AND s.conversationId IN :convIds
           """)
    List<ConversationSettings> findForConversations(
            @Param("userId") Long userId,
            @Param("convIds") Collection<Long> convIds);

    /** Used when a chat folder is deleted — null out the pointer
     *  without touching the rest of the row. */
    @Modifying
    @Transactional
    @Query("""
           UPDATE ConversationSettings s SET s.folderId = NULL
            WHERE s.userId = :userId AND s.folderId = :folderId
           """)
    int detachFolder(
            @Param("userId") Long userId, @Param("folderId") Long folderId);

    long countByUserIdAndPinnedTrue(Long userId);
}
