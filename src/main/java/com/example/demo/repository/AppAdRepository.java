package com.example.demo.repository;

import com.example.demo.entity.AppAd;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AppAdRepository extends JpaRepository<AppAd, Long> {

    List<AppAd> findByActiveTrueAndPlacementOrderBySortOrderAsc(String placement);

    List<AppAd> findAllByOrderBySortOrderAscCreatedAtDesc();
}
