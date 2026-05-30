package com.example.demo.repository;

import com.example.demo.entity.VaultItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VaultItemRepository extends JpaRepository<VaultItem, Long> {
    List<VaultItem> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);
    Optional<VaultItem> findByBlobId(String blobId);
}
