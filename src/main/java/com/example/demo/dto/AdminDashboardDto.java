package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AdminDashboardDto {
    private AdminStatsDto stats;

    /** Posts created per day, last 14 days (oldest → newest). */
    private List<DailyMetric> postsTrend;

    /** Comments created per day, last 14 days (oldest → newest). */
    private List<DailyMetric> commentsTrend;

    /** Top 10 users by followers. */
    private List<AdminUserDto> topUsers;

    /** 5 most recent posts. */
    private List<AdminPostDto> recentPosts;

    /** 5 most recent comments. */
    private List<AdminCommentDto> recentComments;

    /** Posts created in last 24 hours. */
    private long postsToday;

    /** Comments created in last 24 hours. */
    private long commentsToday;

    /** Currently active (non-expired) stories. */
    private long activeStories;

    @Data
    @Builder
    public static class DailyMetric {
        private String date;   // ISO-8601 e.g. 2026-05-20
        private long count;
    }
}
