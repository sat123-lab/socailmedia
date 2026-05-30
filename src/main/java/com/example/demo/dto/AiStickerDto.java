package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AiStickerDto {
    private String emoji;
    private String label;
    private String text;
}
