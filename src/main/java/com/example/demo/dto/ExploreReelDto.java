package com.example.demo.dto;

import com.example.demo.entity.Reel;
import com.example.demo.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Lighter than {@link ReelDto} — only the fields the Explore grid actually
 * paints (thumbnail, counts, creator chrome). Shaving every byte we don't
 * paint keeps the home payload small on cellular.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExploreReelDto {

    private Long id;

    private Long userId;
    private String userName;
    private String userProfileImage;
    private boolean userVerified;

    private String thumbnailUrl;
    private String caption;
    private Integer width;
    private Integer height;

    private long viewsCount;
    private int likesCount;
    private double trendingScore;

    public static ExploreReelDto from(Reel r, User u) {
        return ExploreReelDto.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .userName(u == null ? null : u.getName())
                .userProfileImage(u == null ? null : u.getProfileImage())
                .userVerified(u != null && u.isVerified())
                .thumbnailUrl(r.getThumbnailUrl())
                .caption(r.getCaption())
                .width(r.getWidth())
                .height(r.getHeight())
                .viewsCount(r.getViewsCount())
                .likesCount(r.getLikesCount())
                .trendingScore(r.getTrendingScore())
                .build();
    }
}
