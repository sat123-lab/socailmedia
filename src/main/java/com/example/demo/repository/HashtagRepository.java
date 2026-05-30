package com.example.demo.repository;

import com.example.demo.entity.Hashtag;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface HashtagRepository extends JpaRepository<Hashtag, Long> {

    Optional<Hashtag> findByTag(String tag);

    List<Hashtag> findAllByOrderByUsageCountDescLastUsedAtDesc(Pageable pageable);

    List<Hashtag> findByTagStartingWithOrderByUsageCountDesc(String prefix, Pageable pageable);

    @Modifying @Transactional
    @Query("UPDATE Hashtag h SET h.usageCount = h.usageCount + 1, h.lastUsedAt = :now WHERE h.id = :id")
    int bumpUsage(@Param("id") Long id, @Param("now") LocalDateTime now);
}
