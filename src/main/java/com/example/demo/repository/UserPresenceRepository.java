package com.example.demo.repository;

import com.example.demo.entity.UserPresence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface UserPresenceRepository extends JpaRepository<UserPresence, Long> {

    @Query("SELECT p FROM UserPresence p WHERE p.userId IN :ids")
    List<UserPresence> findAllByUserIds(@Param("ids") Collection<Long> ids);

    /** Reaper — flips users whose heartbeat is older than {@code cutoff}
     *  back to offline so the next presence read reflects reality. */
    @Modifying
    @Transactional
    @Query("""
           UPDATE UserPresence p SET p.online = false
            WHERE p.online = true AND p.lastSeenAt < :cutoff
           """)
    int reapStale(@Param("cutoff") Instant cutoff);
}
