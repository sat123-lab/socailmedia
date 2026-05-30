package com.example.demo.controller;

import com.example.demo.dto.SearchResultsDto;
import com.example.demo.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Public read-only search endpoints.
 *
 * <ul>
 *   <li>{@code GET /search?q=...&limit=10}      — unified, parallel sections.</li>
 *   <li>{@code GET /search/hashtags/trending}   — global hot-tags list.</li>
 *   <li>{@code GET /search/hashtags/suggest?seed=...}</li>
 * </ul>
 */
@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping
    public SearchResultsDto all(@RequestParam(name = "q", required = false) String q,
                                @RequestParam(defaultValue = "10") int limit) {
        return searchService.searchAll(q, limit);
    }

    @GetMapping("/hashtags/trending")
    public List<Map<String, Object>> trending(
            @RequestParam(defaultValue = "20") int limit) {
        return searchService.trendingHashtags(limit);
    }

    @GetMapping("/hashtags/suggest")
    public List<String> suggest(@RequestParam(name = "seed", required = false) String seed,
                                @RequestParam(defaultValue = "10") int limit) {
        return searchService.suggestHashtags(seed, limit);
    }
}
