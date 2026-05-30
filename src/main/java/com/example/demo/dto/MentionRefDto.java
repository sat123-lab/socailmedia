package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One mention chip inside a comment.
 *
 * <p>The client uses {@link #startIndex} / {@link #endIndex} to wrap
 * the matching substring with a clickable widget without re-running
 * the mention regex; {@link #userId} is the deep-link target.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MentionRefDto {
    private Long userId;
    private String display;
    private int startIndex;
    private int endIndex;
    private String name;
    private String profileImage;
    private boolean verified;
}
