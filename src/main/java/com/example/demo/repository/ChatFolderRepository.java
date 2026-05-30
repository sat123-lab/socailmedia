package com.example.demo.repository;

import com.example.demo.entity.ChatFolder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChatFolderRepository extends JpaRepository<ChatFolder, Long> {

    List<ChatFolder> findByUserIdOrderBySortOrderAscIdAsc(Long userId);

    Optional<ChatFolder> findByIdAndUserId(Long id, Long userId);

    long countByUserId(Long userId);
}
