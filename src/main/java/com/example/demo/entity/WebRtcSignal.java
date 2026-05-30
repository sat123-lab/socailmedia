package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "webrtc_signals", indexes = {
        @Index(name = "idx_webrtc_ctx", columnList = "contextType,contextId,createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebRtcSignal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** CALL or ROOM */
    @Column(nullable = false, length = 16)
    private String contextType;

    @Column(nullable = false)
    private Long contextId;

    @Column(nullable = false)
    private Long fromUserId;

    /** Null = broadcast (room offers to all) */
    private Long toUserId;

    /** offer, answer, ice */
    @Column(nullable = false, length = 16)
    private String signalType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
