package com.example.demo.dto;

import com.example.demo.entity.Reel;
import com.example.demo.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Wire format for a single reel — denormalized with the creator's basic
 * profile fields so the client doesn't need a second round trip per reel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReelDto {
    private Long id;
    private Long userId;
    private String userName;
    private String userProfileImage;
    private boolean userVerified;

    private String caption;
    private String videoUrl;
    private String thumbnailUrl;
    private String audioUrl;
    private String audioTitle;
    private int durationSeconds;
    private Integer width;
    private Integer height;

    private int likesCount;
    private int commentsCount;
    private int sharesCount;
    private long viewsCount;

    private String hashtags;
    private String visibility;
    private LocalDateTime createdAt;

    /** Set by the service when the caller is known — server-rendered like state. */
    private boolean likedByMe;

    public static ReelDto from(Reel r, User u, boolean likedByMe) {
        return ReelDto.builder()
                .id(r.getId())
                .userId(r.getUserId())
                .userName(u == null ? null : u.getName())
                .userProfileImage(u == null ? null : u.getProfileImage())
                .userVerified(u != null && u.isVerified())
                .caption(r.getCaption())
                .videoUrl(r.getVideoUrl())
                .thumbnailUrl(r.getThumbnailUrl())
                .audioUrl(r.getAudioUrl())
                .audioTitle(r.getAudioTitle())
                .durationSeconds(r.getDurationSeconds())
                .width(r.getWidth())
                .height(r.getHeight())
                .likesCount(r.getLikesCount())
                .commentsCount(r.getCommentsCount())
                .sharesCount(r.getSharesCount())
                .viewsCount(r.getViewsCount())
                .hashtags(r.getHashtags())
                .visibility(r.getVisibility())
                .createdAt(r.getCreatedAt())
                .likedByMe(likedByMe)
                .build();
    }
}
