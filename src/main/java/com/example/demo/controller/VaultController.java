package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.entity.VaultItem;
import com.example.demo.repository.UserRepository;
import com.example.demo.repository.VaultItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Private encrypted vault.
 *
 * <p>The client encrypts the file body + filename locally with its vault key
 * before upload. The server stores:</p>
 *
 * <ol>
 *   <li>The opaque ciphertext blob on disk (under {@code uploads/vault/}).</li>
 *   <li>A {@link VaultItem} row carrying only encrypted metadata + nonces.</li>
 * </ol>
 *
 * <p>The vault key never reaches the server. Even a full DB + disk dump is
 * useless to an attacker without the device's secret store.</p>
 */
@RestController
@RequestMapping("/vault")
@RequiredArgsConstructor
public class VaultController {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private final VaultItemRepository repository;
    private final UserRepository userRepository;

    // ============================================================
    //  Upload encrypted blob
    // ============================================================
    @PostMapping("/upload")
    public ResponseEntity<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String encryptedFilename,
            @RequestParam(required = false) String encryptedMimeType,
            @RequestParam(required = false) String metadataNonce,
            @RequestParam String contentNonce,
            @RequestParam(defaultValue = "FILE") String kind,
            Authentication authentication) throws IOException {

        User owner = requireUser(authentication);

        String blobId = UUID.randomUUID().toString().replace("-", "");
        Path dir = Paths.get(uploadDir, "vault");
        Files.createDirectories(dir);
        Path target = dir.resolve(blobId + ".bin");
        try (var in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        VaultItem item = VaultItem.builder()
                .ownerId(owner.getId())
                .blobId(blobId)
                .encryptedFilename(encryptedFilename)
                .encryptedMimeType(encryptedMimeType)
                .metadataNonce(metadataNonce)
                .contentNonce(contentNonce)
                .sizeBytes(file.getSize())
                .kind(kind)
                .build();
        repository.save(item);

        return ResponseEntity.ok(Map.of(
                "id", item.getId(),
                "blobId", blobId,
                "sizeBytes", item.getSizeBytes()
        ));
    }

    // ============================================================
    //  List
    // ============================================================
    @GetMapping
    public List<VaultItem> list(Authentication authentication) {
        User owner = requireUser(authentication);
        return repository.findByOwnerIdOrderByCreatedAtDesc(owner.getId());
    }

    // ============================================================
    //  Download encrypted blob — client decrypts locally
    // ============================================================
    @GetMapping("/blob/{blobId}")
    public ResponseEntity<Resource> download(@PathVariable String blobId,
                                             Authentication authentication) {
        User owner = requireUser(authentication);
        Optional<VaultItem> opt = repository.findByBlobId(blobId);
        if (opt.isEmpty() || !opt.get().getOwnerId().equals(owner.getId())) {
            return ResponseEntity.status(404).build();
        }

        Path file = Paths.get(uploadDir, "vault", blobId + ".bin").normalize();
        if (!Files.exists(file)) return ResponseEntity.notFound().build();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new FileSystemResource(file));
    }

    // ============================================================
    //  Delete
    // ============================================================
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id,
                                                      Authentication authentication) throws IOException {
        User owner = requireUser(authentication);
        Optional<VaultItem> opt = repository.findById(id);
        if (opt.isEmpty() || !opt.get().getOwnerId().equals(owner.getId())) {
            return ResponseEntity.status(404).build();
        }
        VaultItem v = opt.get();
        Files.deleteIfExists(Paths.get(uploadDir, "vault", v.getBlobId() + ".bin"));
        repository.delete(v);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    private User requireUser(Authentication a) {
        if (a == null) throw new SecurityException("Not authenticated");
        return userRepository.findByEmail(a.getName())
                .orElseThrow(() -> new SecurityException("User not found"));
    }
}
