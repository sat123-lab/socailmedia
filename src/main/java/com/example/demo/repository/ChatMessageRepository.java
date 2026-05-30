package com.example.demo.repository;

import com.example.demo.entity.ChatMessage;
import com.example.demo.entity.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface ChatMessageRepository
        extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationOrderByCreatedAtAsc(
            Conversation conversation
    );

    ChatMessage findFirstByConversationOrderByCreatedAtDesc(
            Conversation conversation
    );

    /** Prevents spamming duplicate "line busy" rows in the same chat. */
    boolean existsByConversationAndMessageTypeAndCallStatusAndCreatedAtAfter(
            Conversation conversation,
            String messageType,
            String callStatus,
            LocalDateTime since
    );

    @Query("SELECT m FROM ChatMessage m " +
            "WHERE m.expiresAt IS NOT NULL " +
            "AND m.expiresAt < :now " +
            "AND m.deletedForEveryone = false")
    List<ChatMessage> findExpired(@Param("now") LocalDateTime now);

    @Modifying
    @Transactional
    @Query("DELETE FROM ChatMessage m WHERE m.id IN :ids")
    int deleteAllByIdInBulk(@Param("ids") List<Long> ids);

    // ---- Admin private-chat monitor: read-only paginated listing ----
    // The query NEVER selects content; it only counts its length so admins
    // see "encrypted size" instead of the body. We project metadata only.
    @Query("""
           SELECT m FROM ChatMessage m
           WHERE (:senderId IS NULL OR m.sender.id = :senderId)
             AND (:from IS NULL OR m.createdAt >= :from)
             AND (:to   IS NULL OR m.createdAt <= :to)
             AND (:encryptedOnly = false OR m.encrypted = true)
           ORDER BY m.createdAt DESC
           """)
    Page<ChatMessage> adminSearch(
            @Param("senderId") Long senderId,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to,
            @Param("encryptedOnly") boolean encryptedOnly,
            Pageable pageable);

    // ---- V6 — paginated history + search --------------------------------

    @Query("""
           SELECT m FROM ChatMessage m
            WHERE m.conversation.id = :convId
              AND m.deletedForEveryone = false
            ORDER BY m.createdAt DESC
           """)
    Page<ChatMessage> findPageByConversation(
            @Param("convId") Long conversationId, Pageable pageable);

    /**
     * Metadata-only search inside one conversation. Encrypted bodies are
     * never matched server-side — only {@code mediaKind}, call logs, and
     * plaintext (non-encrypted) content.
     */
    @Query("""
           SELECT m FROM ChatMessage m
            WHERE m.conversation.id = :convId
              AND m.deletedForEveryone = false
              AND (
                    (m.encrypted = false AND LOWER(m.content) LIKE LOWER(CONCAT('%', :q, '%')))
                 OR (:mediaKind IS NOT NULL AND m.mediaKind = :mediaKind)
              )
            ORDER BY m.createdAt DESC
           """)
    Page<ChatMessage> searchInConversation(
            @Param("convId") Long conversationId,
            @Param("q") String query,
            @Param("mediaKind") String mediaKind,
            Pageable pageable);

    @Query("""
           SELECT m FROM ChatMessage m
            WHERE (m.conversation.userOne.id = :userId OR m.conversation.userTwo.id = :userId)
              AND m.deletedForEveryone = false
              AND m.encrypted = false
              AND LOWER(m.content) LIKE LOWER(CONCAT('%', :q, '%'))
            ORDER BY m.createdAt DESC
           """)
    Page<ChatMessage> searchGlobalForUser(
            @Param("userId") Long userId,
            @Param("q") String query,
            Pageable pageable);

}
