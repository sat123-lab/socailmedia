package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(

        name = "followers",

        uniqueConstraints = {

                @UniqueConstraint(

                        columnNames = {
                                "follower_id",
                                "following_id"
                        }
                )
        }
)

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder

public class Follow {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )

    private Long id;

    // =========================
    // WHO IS FOLLOWING
    // =========================

    @ManyToOne(fetch = FetchType.LAZY)

    @JoinColumn(name = "follower_id")

    @JsonIgnoreProperties({

            "password",
            "posts",
            "comments",
            "likes",
            "followers",
            "following"
    })

    private User follower;

    // =========================
    // WHO IS BEING FOLLOWED
    // =========================

    @ManyToOne(fetch = FetchType.LAZY)

    @JoinColumn(name = "following_id")

    @JsonIgnoreProperties({

            "password",
            "posts",
            "comments",
            "likes",
            "followers",
            "following"
    })

    private User following;

    // =========================
    // CREATED TIME
    // =========================

    private LocalDateTime createdAt;

    // =========================
    // AUTO TIME
    // =========================

    @PrePersist

    public void prePersist() {

        if (createdAt == null) {

            createdAt =
                    LocalDateTime.now();
        }
    }
}