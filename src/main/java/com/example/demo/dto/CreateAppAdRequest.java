package com.example.demo.dto;

import lombok.Data;

@Data
public class CreateAppAdRequest {
    private String title;
    private String subtitle;
    private String imageUrl;
    private String targetUrl;
    private String placement;
    private Boolean active;
    private Integer sortOrder;
}
