package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * A user-submitted abuse report against a comment.
 *
 * <p>The unique constraint on {@code (comment_id, reporter_user_id)}
 * keeps the report endpoint idempotent — re-submitting the same
 * report has no effect, but a second different user can still file
 * their own report against the same comment.
 *
 * <p>Status lifecycle: {@code OPEN → REVIEWING → ACTIONED|DISMISSED}.
 * The admin queue is fed by {@code CommentReportRepository.findOpen(...)}.
 */
@Entity
@Table(name = "comment_reports",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_comment_reports",
                        columnNames = {"comment_id", "reporter_user_id"})
        },
        indexes = {
                @Index(name = "idx_comment_reports_status_created",
                        columnList = "status, created_at")
        })
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommentReport {

    public enum Status { OPEN, REVIEWING, ACTIONED, DISMISSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "comment_id", nullable = false)
    private Long commentId;

    @Column(name = "reporter_user_id", nullable = false)
    private Long reporterUserId;

    /** Short slug — spam / harassment / hate / nsfw / self-harm / other. */
    @Column(length = 32, nullable = false)
    private String reason;

    /** Optional free-form note from the reporter. */
    @Column(length = 500)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    @Builder.Default
    private Status status = Status.OPEN;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = Status.OPEN;
    }
}
