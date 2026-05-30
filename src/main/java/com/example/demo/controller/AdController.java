package com.example.demo.controller;

import com.example.demo.dto.AppAdDto;
import com.example.demo.service.AppAdService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/ads")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AdController {

    private final AppAdService appAdService;

    @GetMapping("/active")
    public List<AppAdDto> active(@RequestParam(defaultValue = "FEED") String placement) {
        return appAdService.activeForPlacement(placement);
    }
}
