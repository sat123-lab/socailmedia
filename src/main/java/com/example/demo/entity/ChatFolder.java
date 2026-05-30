package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Telegram-style user-defined folder for organising chats.
 *
 * <p>Each user has their own folder list — folders are never shared
 * between users. Built-in folders (Unread, Groups, Creators, Business)
 * are derived on the fly server-side and don't need rows here; this
 * table only holds the user's <i>custom</i> folders.</p>
 */
@Entity
@Table(name = "chat_folders",
        indexes = @Index(name = "idx_chat_folders_user",
                         columnList = "user_id,sort_order"))
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, length = 64)
    private String name;

    /** Optional decorative emoji shown in the folder pill. */
    @Column(length = 16)
    private String emoji;

    /** CUSTOM | FAVORITES | WORK | PERSONAL — used to colour the pill. */
    @Column(nullable = false, length = 16)
    @Builder.Default
    private String kind = "CUSTOM";

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
