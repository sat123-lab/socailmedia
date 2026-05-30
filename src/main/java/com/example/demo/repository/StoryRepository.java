package com.example.demo.repository;

import com.example.demo.entity.Story;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface StoryRepository extends JpaRepository<Story, Long> {

    List<Story> findByUserAndExpiresAtAfterOrderByCreatedAtAsc(
            User user,
            LocalDateTime now
    );

    boolean existsByUserAndExpiresAtAfter(
            User user,
            LocalDateTime now
    );

    /**
     * Lightweight active-story probe used by the Explore Hub — avoids
     * the User → Story join when we only need a boolean.
     */
    @Query("""
           SELECT COUNT(s) > 0 FROM Story s
            WHERE s.user.id = :userId AND s.expiresAt > :now
           """)
    boolean existsActiveByUser(@Param("userId") Long userId,
                                @Param("now") LocalDateTime now);
}
