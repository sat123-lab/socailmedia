package com.example.demo.repository;

import com.example.demo.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserRepository
        extends JpaRepository<User, Long> {

    // =========================
    // LOGIN WITH EMAIL
    // =========================

    Optional<User> findByEmail(
            String email
    );

    // =========================
    // CHECK PHONE EXISTS
    // =========================

    Optional<User> findByPhone(
            String phone
    );

    // =========================
    // SEARCH USERS
    // =========================

    List<User>
    findByNameContainingIgnoreCase(
            String name
    );

    /**
     * Used by the unified search endpoint. Verified accounts and accounts with
     * more followers float to the top.
     */
    @Query("""
           SELECT u FROM User u
           WHERE LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
           ORDER BY u.verified DESC, u.followersCount DESC, u.id ASC
           """)
    List<User> searchPaged(@Param("q") String q, Pageable pageable);

    // =========================
    // CHECK EMAIL EXISTS
    // =========================

    boolean existsByEmail(
            String email
    );

    // =========================
    // CHECK PHONE EXISTS
    // =========================

    boolean existsByPhone(
            String phone
    );

    // =========================
    // GET USER BY ID
    // =========================

    Optional<User> findById(
            Long id
    );

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.followingCount = :count WHERE u.id = :id")
    void updateFollowingCount(
            @Param("id") Long id,
            @Param("count") int count
    );

    @Modifying
    @Transactional
    @Query("UPDATE User u SET u.followersCount = :count WHERE u.id = :id")
    void updateFollowersCount(
            @Param("id") Long id,
            @Param("count") int count
    );

    // =========================
    // REFERRAL & INVITE
    // =========================

    /**
     * Case-insensitive lookup by referral code. The DB column has a
     * unique index, but we always normalise to upper-case before
     * hitting it so "abcd1234" and "ABCD1234" resolve to the same user.
     */
    @Query("SELECT u FROM User u WHERE UPPER(u.referralCode) = UPPER(:code)")
    Optional<User> findByReferralCodeIgnoreCase(@Param("code") String code);

    boolean existsByReferralCode(String code);

    // =========================
    // DISCOVERY (Explore Hub)
    // =========================

    /**
     * Newly joined creator-type accounts. Ordered by id DESC as a proxy
     * for sign-up time — the User entity doesn't carry a createdAt column,
     * but the IDENTITY primary key is monotonic so this gives a correct
     * "newest first" order without a schema change.
     */
    @Query("""
           SELECT u FROM User u
           WHERE u.accountType <> 'PERSONAL'
             AND u.privateAccount = false
           ORDER BY u.id DESC
           """)
    List<User> findNewCreators(Pageable pageable);

    /**
     * Verified creators ordered by followers — used by the Creator
     * Discovery "Verified" tab.
     */
    @Query("""
           SELECT u FROM User u
           WHERE u.verified = true AND u.privateAccount = false
           ORDER BY u.followersCount DESC, u.id DESC
           """)
    List<User> findVerifiedCreators(Pageable pageable);

    /**
     * Creators filtered by their declared category (e.g. "Travel",
     * "Comedy"). The {@code category} column was added in V7.
     */
    @Query("""
           SELECT u FROM User u
           WHERE u.category = :category
             AND u.accountType <> 'PERSONAL'
             AND u.privateAccount = false
           ORDER BY u.followersCount DESC, u.id DESC
           """)
    List<User> findCreatorsByCategory(@Param("category") String category,
                                       Pageable pageable);
}