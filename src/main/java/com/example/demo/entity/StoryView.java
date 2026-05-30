package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "story_views",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"story_id", "viewer_id"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoryView {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "story_id")
    private Long storyId;

    @Column(name = "viewer_id")
    private Long viewerId;

    private LocalDateTime viewedAt;

    @PrePersist
    public void prePersist() {
        if (viewedAt == null) {
            viewedAt = LocalDateTime.now();
        }
    }
}
