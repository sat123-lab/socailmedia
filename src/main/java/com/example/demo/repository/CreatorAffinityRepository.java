package com.example.demo.repository;

import com.example.demo.entity.CreatorAffinity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CreatorAffinityRepository extends JpaRepository<CreatorAffinity, Long> {

    Optional<CreatorAffinity> findByUserIdAndCreatorId(Long userId, Long creatorId);

    @Query("""
           SELECT c FROM CreatorAffinity c
            WHERE c.userId = :userId
            ORDER BY c.score DESC
           """)
    List<CreatorAffinity> topByUser(@Param("userId") Long userId, Pageable page);

    /**
     * Naive collaborative-filter step — find creators followed/loved by the
     * users I have the highest affinity with, that I myself have NOT
     * followed or already interacted with strongly.
     *
     * <p>Implemented as a 2-hop join over the affinity table: we look at
     * users similar to me (sharing my top creators), gather the creators
     * <i>they</i> love most, and rank by aggregate score.</p>
     *
     * <p><b>MySQL portability:</b> MySQL versions before 8.0.14 reject
     * {@code LIMIT} inside an {@code IN (subquery)} with
     * {@code "This version of MySQL doesn't yet support
     * 'LIMIT & IN/ALL/ANY/SOME subquery'"}. We work around it by
     * wrapping the limited subquery in a derived table — every MySQL
     * since 5.6 happily accepts {@code IN (SELECT ... FROM (...) t)}.</p>
     */
    @Query(value = """
            SELECT a2.creator_id AS creator_id, SUM(a2.score) AS total
              FROM creator_affinity a1
              JOIN creator_affinity a2 ON a2.user_id = a1.user_id
             WHERE a1.creator_id IN (
                     SELECT t.creator_id FROM (
                       SELECT creator_id FROM creator_affinity
                        WHERE user_id = :userId
                        ORDER BY score DESC
                        LIMIT 10
                     ) t
                   )
               AND a1.user_id  <> :userId
               AND a2.creator_id <> :userId
               AND a2.creator_id NOT IN (
                     SELECT t2.creator_id FROM (
                       SELECT creator_id FROM creator_affinity
                        WHERE user_id = :userId
                     ) t2
                   )
             GROUP BY a2.creator_id
             ORDER BY total DESC
             LIMIT :max
           """, nativeQuery = true)
    List<Object[]> recommendByCoOccurrence(
            @Param("userId") Long userId, @Param("max") int max);
}
