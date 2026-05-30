package com.example.demo.repository;

import com.example.demo.entity.CloseFriend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface CloseFriendRepository extends JpaRepository<CloseFriend, Long> {

    List<CloseFriend> findByUserId(Long userId);

    @Query("SELECT cf.friendId FROM CloseFriend cf WHERE cf.userId = :userId")
    List<Long> findFriendIds(@Param("userId") Long userId);

    boolean existsByUserIdAndFriendId(Long userId, Long friendId);

    @Modifying @Transactional
    @Query("DELETE FROM CloseFriend cf WHERE cf.userId = :userId AND cf.friendId = :friendId")
    int delete(@Param("userId") Long userId, @Param("friendId") Long friendId);
}
