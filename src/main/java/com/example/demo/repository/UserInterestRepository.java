package com.example.demo.repository;

import com.example.demo.entity.UserInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserInterestRepository extends JpaRepository<UserInterest, Long> {

    Optional<UserInterest> findByUserIdAndTopic(Long userId, String topic);

    /** Top-N topics the user is currently interested in. */
    @Query("""
           SELECT u FROM UserInterest u
            WHERE u.userId = :userId
            ORDER BY u.score DESC
           """)
    List<UserInterest> topByUser(@Param("userId") Long userId,
                                  org.springframework.data.domain.Pageable page);

    List<UserInterest> findAllByUserId(Long userId);
}
