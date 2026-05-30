package com.example.demo.service;

import com.example.demo.dto.AppAdDto;
import com.example.demo.dto.CreateAppAdRequest;
import com.example.demo.entity.AppAd;
import com.example.demo.entity.User;
import com.example.demo.repository.AppAdRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppAdService {

    private final AppAdRepository adRepository;
    private final UserRepository userRepository;

    private void requireAdmin(Authentication auth) {
        User me = userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        if (!me.isAdmin()) {
            throw new RuntimeException("Admin only");
        }
    }

    public List<AppAdDto> activeForPlacement(String placement) {
        String p = placement != null && !placement.isBlank() ? placement.toUpperCase() : "FEED";
        return adRepository.findByActiveTrueAndPlacementOrderBySortOrderAsc(p).stream()
                .map(AppAdDto::from)
                .collect(Collectors.toList());
    }

    public List<AppAdDto> listAll(Authentication auth) {
        requireAdmin(auth);
        return adRepository.findAllByOrderBySortOrderAscCreatedAtDesc().stream()
                .map(AppAdDto::from)
                .collect(Collectors.toList());
    }

    @Transactional
    public AppAdDto create(CreateAppAdRequest body, Authentication auth) {
        requireAdmin(auth);
        if (body.getTitle() == null || body.getTitle().isBlank()) {
            throw new RuntimeException("Title required");
        }
        if (body.getImageUrl() == null || body.getImageUrl().isBlank()) {
            throw new RuntimeException("Image URL required");
        }
        AppAd saved = adRepository.save(AppAd.builder()
                .title(body.getTitle().trim())
                .subtitle(body.getSubtitle() != null ? body.getSubtitle().trim() : "")
                .imageUrl(body.getImageUrl().trim())
                .targetUrl(body.getTargetUrl() != null ? body.getTargetUrl().trim() : "")
                .placement(body.getPlacement() != null ? body.getPlacement().toUpperCase() : "FEED")
                .active(body.getActive() == null || body.getActive())
                .sortOrder(body.getSortOrder() != null ? body.getSortOrder() : 0)
                .build());
        return AppAdDto.from(saved);
    }

    @Transactional
    public AppAdDto update(Long id, CreateAppAdRequest body, Authentication auth) {
        requireAdmin(auth);
        AppAd ad = adRepository.findById(id).orElseThrow(() -> new RuntimeException("Ad not found"));
        if (body.getTitle() != null && !body.getTitle().isBlank()) {
            ad.setTitle(body.getTitle().trim());
        }
        if (body.getSubtitle() != null) ad.setSubtitle(body.getSubtitle().trim());
        if (body.getImageUrl() != null && !body.getImageUrl().isBlank()) {
            ad.setImageUrl(body.getImageUrl().trim());
        }
        if (body.getTargetUrl() != null) ad.setTargetUrl(body.getTargetUrl().trim());
        if (body.getPlacement() != null) ad.setPlacement(body.getPlacement().toUpperCase());
        if (body.getActive() != null) ad.setActive(body.getActive());
        if (body.getSortOrder() != null) ad.setSortOrder(body.getSortOrder());
        return AppAdDto.from(adRepository.save(ad));
    }

    @Transactional
    public void delete(Long id, Authentication auth) {
        requireAdmin(auth);
        adRepository.deleteById(id);
    }
}
