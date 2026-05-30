package com.example.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class AiResponseDto {
    private String reply;
    private List<String> items;
    private List<AiStickerDto> stickers;
}
