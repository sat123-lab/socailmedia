package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "story_highlight_items",
       indexes = @Index(name = "idx_highlight_items_hl", columnList = "highlightId, sortOrder"))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StoryHighlightItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false) private Long highlightId;
    @Column(nullable = false) private Long storyId;

    @Builder.Default
    @Column(nullable = false)
    private int sortOrder = 0;
}
