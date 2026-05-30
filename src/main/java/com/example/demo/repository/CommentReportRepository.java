package com.example.demo.repository;

import com.example.demo.entity.CommentReport;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CommentReportRepository
        extends JpaRepository<CommentReport, Long> {

    Optional<CommentReport> findByCommentIdAndReporterUserId(Long commentId,
                                                              Long reporterUserId);

    long countByCommentId(Long commentId);

    /** Admin queue feed. */
    @Query("""
           SELECT r FROM CommentReport r
            WHERE r.status = com.example.demo.entity.CommentReport.Status.OPEN
            ORDER BY r.createdAt DESC
           """)
    List<CommentReport> findOpen(Pageable page);
}
