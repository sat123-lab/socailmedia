package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * One chat message inside a {@link LiveStream}. Persisted so we can:
 *
 * <ul>
 *   <li>Replay the chat in the optional "watch later" mode.</li>
 *   <li>Surface pinned messages even after a viewer reconnects.</li>
 *   <li>Show creators a moderation audit trail.</li>
 * </ul>
 */
@Entity
@Table(name = "live_stream_messages", indexes = {
        @Index(name = "idx_lsm_stream_created", columnList = "stream_id,created_at"),
        @Index(name = "idx_lsm_sender_created", columnList = "sender_id,created_at"),
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LiveStreamMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stream_id")
    private LiveStream stream;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @Column(nullable = false, length = 280)
    private String body;

    /** {@code CHAT}, {@code JOIN}, {@code REACT}, {@code GIFT}, {@code SYSTEM}. */
    @Column(nullable = false, length = 16)
    @Builder.Default
    private String kind = "CHAT";

    @Builder.Default
    private Boolean pinned = false;

    @Builder.Default
    private Boolean deleted = false;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
