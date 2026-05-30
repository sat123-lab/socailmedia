package com.example.demo.dto;

import com.example.demo.entity.AppAd;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AppAdDto {
    private Long id;
    private String title;
    private String subtitle;
    private String imageUrl;
    private String targetUrl;
    private String placement;
    private boolean active;
    private int sortOrder;

    public static AppAdDto from(AppAd a) {
        return AppAdDto.builder()
                .id(a.getId())
                .title(a.getTitle())
                .subtitle(a.getSubtitle())
                .imageUrl(a.getImageUrl())
                .targetUrl(a.getTargetUrl())
                .placement(a.getPlacement())
                .active(a.isActive())
                .sortOrder(a.getSortOrder())
                .build();
    }
}
