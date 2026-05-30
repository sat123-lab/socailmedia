package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class User {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )

    private Long id;

    // BASIC INFO

    private String name;

    @Column(unique = true)

    private String phone;

    @Column(unique = true)

    private String email;

    @JsonIgnore

    private String password;

    private String googleId;

    private String profileImage;

    @Column(length = 500)

    private String bio;

    private String authProvider;

    // POSTS

    @JsonIgnore

    @OneToMany(

            mappedBy = "user",

            cascade = CascadeType.ALL,

            orphanRemoval = true
    )

    @lombok.Builder.Default
    private List<Post> posts =
            new ArrayList<>();

    // COMMENTS

    @JsonIgnore

    @OneToMany(

            mappedBy = "user",

            cascade = CascadeType.ALL,

            orphanRemoval = true
    )

    @lombok.Builder.Default
    private List<Comment> comments =
            new ArrayList<>();

    // LIKES

    @JsonIgnore

    @OneToMany(

            mappedBy = "user",

            cascade = CascadeType.ALL,

            orphanRemoval = true
    )

    @lombok.Builder.Default
    private List<Like> likes =
            new ArrayList<>();

    // FOLLOWERS

    @JsonIgnore

    @OneToMany(

            mappedBy = "following",

            cascade = CascadeType.ALL,

            orphanRemoval = true
    )

    @lombok.Builder.Default
    private List<Follow> followers =
            new ArrayList<>();

    // FOLLOWING

    @JsonIgnore

    @OneToMany(

            mappedBy = "follower",

            cascade = CascadeType.ALL,

            orphanRemoval = true
    )

    @lombok.Builder.Default
    private List<Follow> following =
            new ArrayList<>();

    // FOLLOWERS COUNT

    @lombok.Builder.Default
    private int followersCount = 0;

    // FOLLOWING COUNT

    @lombok.Builder.Default
    private int followingCount = 0;

    // ACCOUNT PRIVACY (false = public)

    @lombok.Builder.Default
    private boolean privateAccount = false;

    @Builder.Default
    private boolean admin = false;

    // -----------------------------------------------------------------
    // CREATOR / VERIFIED ACCOUNT (Phase 8)
    // -----------------------------------------------------------------

    /** Blue-tick verification — granted by an admin via /admin/users/{id}/verify. */
    @Builder.Default
    @jakarta.persistence.Column(nullable = false)
    private boolean verified = false;

    /** PERSONAL · CREATOR · BUSINESS. Controls the analytics surface. */
    @Builder.Default
    @jakarta.persistence.Column(length = 16, nullable = false)
    private String accountType = "PERSONAL";

    @jakarta.persistence.Column(length = 256)
    private String website;

    /** Creator-account category, e.g. "Travel", "Comedy", "Tech". */
    @jakarta.persistence.Column(length = 64)
    private String category;

    /** Admin tier. NULL for non-admin users; otherwise one of SUPPORT/MODERATOR/SUPER_ADMIN. */
    @jakarta.persistence.Column(length = 32)
    private String adminRole;

    /** Last successful admin-panel login. Used by AdminAccessGuard for session TTL. */
    private java.time.LocalDateTime lastAdminLoginAt;

    // LAST SEEN (for online status)

    private java.time.LocalDateTime lastSeenAt;

    // -----------------------------------------------------------------
    // REFERRAL & INVITE SYSTEM
    // -----------------------------------------------------------------

    /**
     * The user's personal invite code. Minted lazily on first access to
     * {@code GET /referrals/me} — older accounts may have null until
     * they open the Invite Friends screen. Globally unique across the
     * users table; case-insensitive comparison is enforced at the
     * service layer.
     */
    @jakarta.persistence.Column(name = "referral_code", length = 16, unique = true)
    private String referralCode;

    /**
     * The {@link User#id} of the inviter, populated when this account
     * was created via a valid referral code. Null for organic signups.
     * Immutable once set so attribution can't be revised after the fact.
     */
    @jakarta.persistence.Column(name = "referred_by_user_id")
    private Long referredByUserId;
}