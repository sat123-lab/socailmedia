package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "follow_requests",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"requester_id", "target_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FollowRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "requester_id")
    @JsonIgnoreProperties({
            "password",
            "posts",
            "comments",
            "likes",
            "followers",
            "following"
    })
    private User requester;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "target_id")
    @JsonIgnoreProperties({
            "password",
            "posts",
            "comments",
            "likes",
            "followers",
            "following"
    })
    private User target;

    private String status;

    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "PENDING";
        }
    }
}
