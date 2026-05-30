package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "conversations",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"user_one_id", "user_two_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_one_id")
    @JsonIgnoreProperties({
            "password", "posts", "comments", "likes", "followers", "following"
    })
    private User userOne;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_two_id")
    @JsonIgnoreProperties({
            "password", "posts", "comments", "likes", "followers", "following"
    })
    private User userTwo;

    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    public void touch() {
        updatedAt = LocalDateTime.now();
    }
}
