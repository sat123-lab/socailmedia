package com.example.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Container for the unified search endpoint. Each section is independent so
 * the Flutter client can render the matching ones progressively as they
 * load (the controller fills them in parallel).
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SearchResultsDto {
    private String query;
    private List<Map<String, Object>> users;
    private List<Map<String, Object>> hashtags;
    private List<Map<String, Object>> posts;
    private List<Map<String, Object>> reels;
}
