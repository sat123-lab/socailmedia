package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Gift / monetization event. Architecture-only for now — the gift
 * catalogue lives in code and {@code giftValue} is treated as a
 * symbolic coin amount, not real currency. A future payment processor
 * plugs in by wrapping the {@code GiftCatalog} interface and creating
 * a row here only after a charge succeeds.
 */
@Entity
@Table(name = "live_stream_gifts", indexes = {
        @Index(name = "idx_lsg_creator_created", columnList = "creator_id,created_at"),
        @Index(name = "idx_lsg_stream_created", columnList = "stream_id,created_at"),
})
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LiveStreamGift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stream_id")
    private LiveStream stream;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id")
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "creator_id")
    private User creator;

    @Column(name = "gift_id", nullable = false, length = 64)
    private String giftId;

    @Column(name = "gift_value", nullable = false)
    @Builder.Default
    private Integer giftValue = 0;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
