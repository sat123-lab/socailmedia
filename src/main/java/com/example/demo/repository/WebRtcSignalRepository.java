package com.example.demo.repository;

import com.example.demo.entity.WebRtcSignal;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebRtcSignalRepository extends JpaRepository<WebRtcSignal, Long> {

    List<WebRtcSignal> findByContextTypeAndContextIdAndIdGreaterThanOrderByIdAsc(
            String contextType,
            Long contextId,
            Long afterId
    );
}
