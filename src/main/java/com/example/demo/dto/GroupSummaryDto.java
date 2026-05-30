package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GroupSummaryDto {
    private Long id;
    private String name;
    private int memberCount;
    private boolean iAmAdmin;
}
