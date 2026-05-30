package com.example.demo.repository;

import com.example.demo.entity.Follow;
import com.example.demo.entity.User;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FollowRepository
        extends JpaRepository<Follow, Long> {

    // =========================
    // CHECK FOLLOWING
    // =========================

    Optional<Follow>
    findByFollowerAndFollowing(

            User follower,

            User following
    );

    // =========================
    // CHECK EXISTS
    // =========================

    boolean existsByFollowerAndFollowing(

            User follower,

            User following
    );

    // =========================
    // FOLLOWERS COUNT
    // =========================

    long countByFollowing(
            User following
    );

    // =========================
    // FOLLOWING COUNT
    // =========================

    long countByFollower(
            User follower
    );

    // =========================
    // UNFOLLOW
    // =========================

    @Transactional

    void deleteByFollowerAndFollowing(

            User follower,

            User following
    );

    // =========================
    // GET FOLLOWERS LIST
    // =========================

    List<Follow> findByFollowing(
            User following
    );

    // =========================
    // GET FOLLOWING LIST
    // =========================

    List<Follow> findByFollower(
            User follower
    );

    // =========================
    // GET FOLLOWERS IDS
    // =========================

    List<Follow>
    findAllByFollowing(
            User following
    );

    // =========================
    // GET FOLLOWING IDS
    // =========================

    List<Follow>
    findAllByFollower(
            User follower
    );

    // ===========================================================
    //  ID-only accessors used by the recommendation engine.
    // ===========================================================

    @org.springframework.data.jpa.repository.Query(
            "SELECT f.follower.id FROM Follow f WHERE f.following.id = :userId")
    java.util.List<Long> findFollowersByUserId(
            @org.springframework.data.repository.query.Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Query(
            "SELECT f.following.id FROM Follow f WHERE f.follower.id = :userId")
    java.util.List<Long> findFollowingByUserId(
            @org.springframework.data.repository.query.Param("userId") Long userId);
}