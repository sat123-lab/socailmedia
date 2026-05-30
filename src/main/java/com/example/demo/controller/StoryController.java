package com.example.demo.controller;

import com.example.demo.dto.StoryGroupDto;
import com.example.demo.entity.Story;
import com.example.demo.service.StoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/stories")
@RequiredArgsConstructor
@CrossOrigin("*")
public class StoryController {

    private final StoryService storyService;

    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public Story uploadStory(
            @RequestPart("media") MultipartFile media,
            Authentication authentication
    ) throws Exception {

        return storyService.uploadStory(
                authentication.getName(),
                media
        );
    }

    @GetMapping("/feed")
    public List<StoryGroupDto> getStoryFeed(
            Authentication authentication
    ) {

        return storyService.getStoryFeed(
                authentication.getName()
        );
    }

    @GetMapping("/active/{userId}")
    public boolean hasActiveStory(
            @PathVariable Long userId
    ) {

        return storyService.hasActiveStory(userId);
    }

    @PostMapping("/view")
    public String markViewed(
            @RequestBody List<Long> storyIds,
            Authentication authentication
    ) {

        storyService.markStoriesViewed(
                authentication.getName(),
                storyIds
        );

        return "Stories marked as viewed";
    }

    @DeleteMapping("/{storyId}")
    public String deleteStory(
            @PathVariable Long storyId,
            Authentication authentication
    ) {

        storyService.deleteStory(
                authentication.getName(),
                storyId
        );

        return "Story deleted";
    }
}
