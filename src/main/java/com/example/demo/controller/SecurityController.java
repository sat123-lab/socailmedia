package com.example.demo.controller;

import com.example.demo.dto.AuthTokensDto;
import com.example.demo.dto.KeyBundleDto;
import com.example.demo.dto.KeyBundleUploadRequest;
import com.example.demo.dto.OneTimePreKeyDto;
import com.example.demo.dto.RefreshTokenRequest;
import com.example.demo.entity.OneTimePreKey;
import com.example.demo.entity.RefreshToken;
import com.example.demo.entity.User;
import com.example.demo.entity.UserIdentityKey;
import com.example.demo.repository.OneTimePreKeyRepository;
import com.example.demo.repository.UserIdentityKeyRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.AuditLogService;
import com.example.demo.security.SecurityProperties;
import com.example.demo.security.TokenManager;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Endpoints under {@code /security/*}.
 *
 * <ul>
 *   <li>{@code POST /security/refresh} — exchange a refresh token for a new access pair.</li>
 *   <li>{@code POST /security/logout} — revoke a refresh token.</li>
 *   <li>{@code POST /security/keys/upload} — upload public key bundle + new pre-keys.</li>
 *   <li>{@code GET /security/keys/{userId}} — fetch a recipient's bundle (burns one pre-key).</li>
 *   <li>{@code GET /security/keys/me/inventory} — how many pre-keys are left on the server.</li>
 * </ul>
 */
@RestController
@RequestMapping("/security")
@RequiredArgsConstructor
public class SecurityController {

    private final TokenManager tokenManager;
    private final UserRepository userRepository;
    private final UserIdentityKeyRepository identityRepo;
    private final OneTimePreKeyRepository preKeyRepo;
    private final SecurityProperties properties;
    private final AuditLogService auditLog;

    // ==================================================================
    //  Refresh / logout
    // ==================================================================

    @PostMapping("/refresh")
    public ResponseEntity<AuthTokensDto> refresh(@RequestBody RefreshTokenRequest req,
                                                 HttpServletRequest http) {
        Optional<RefreshToken> stored = tokenManager.validateRefreshToken(req.getRefreshToken());
        if (stored.isEmpty()) {
            auditLog.record(AuditLogService.TOKEN_REUSE, null, null, "RefreshToken",
                    "invalid refresh token", http);
            return ResponseEntity.status(401).build();
        }

        User user = userRepository.findById(stored.get().getUserId()).orElse(null);
        if (user == null) {
            return ResponseEntity.status(401).build();
        }

        String newRefresh = tokenManager.rotateRefreshToken(stored.get(), user);
        String access = tokenManager.issueAccessToken(user);
        auditLog.record(AuditLogService.TOKEN_REFRESH, user.getId(), "refresh");

        return ResponseEntity.ok(AuthTokensDto.builder()
                .accessToken(access)
                .refreshToken(newRefresh)
                .accessExpiresIn(properties.getJwt().getAccessTtlSeconds())
                .refreshExpiresIn(properties.getJwt().getRefreshTtlSeconds())
                .userId(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .admin(user.isAdmin())
                .build());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest http) {
        User user = currentUser(authentication);
        if (user != null) {
            tokenManager.revokeAllForUser(user.getId());
            auditLog.record(AuditLogService.LOGOUT, user.getId(), null, null, "logout", http);
        }
        return ResponseEntity.ok().build();
    }

    // ==================================================================
    //  E2EE key bundle
    // ==================================================================

    /** Upload (or replace) the caller's identity bundle + a batch of fresh one-time keys. */
    @PostMapping("/keys/upload")
    public ResponseEntity<Map<String, Object>> uploadKeyBundle(
            @RequestBody KeyBundleUploadRequest req,
            Authentication authentication,
            HttpServletRequest http) {

        User user = requireUser(authentication);

        UserIdentityKey ik = identityRepo.findByUserId(user.getId()).orElseGet(UserIdentityKey::new);
        ik.setUserId(user.getId());
        ik.setIdentityPublicKeyBase64(req.getIdentityPublicKey());
        ik.setSigningPublicKeyBase64(req.getSigningPublicKey());
        ik.setAlgorithm(req.getAlgorithm() == null ? "X25519-Ed25519-AESGCM" : req.getAlgorithm());
        ik.setKeyVersion(req.getKeyVersion() == null ? 1 : req.getKeyVersion());
        identityRepo.save(ik);

        int added = 0;
        if (req.getOneTimePreKeys() != null) {
            for (OneTimePreKeyDto k : req.getOneTimePreKeys()) {
                if (k == null || k.getPublicKey() == null) continue;
                String keyId = k.getKeyId() == null ? UUID.randomUUID().toString() : k.getKeyId();
                if (preKeyRepo.findByUserIdAndKeyId(user.getId(), keyId).isPresent()) continue;
                preKeyRepo.save(OneTimePreKey.builder()
                        .userId(user.getId())
                        .keyId(keyId)
                        .publicKeyBase64(k.getPublicKey())
                        .signatureBase64(k.getSignature())
                        .used(false)
                        .createdAt(LocalDateTime.now())
                        .build());
                added++;
            }
        }

        auditLog.record(AuditLogService.KEY_BUNDLE_UPLOAD, user.getId(), null, null,
                "version=" + ik.getKeyVersion() + " preKeys=" + added, http);

        return ResponseEntity.ok(Map.of(
                "keyVersion", ik.getKeyVersion(),
                "preKeysAdded", added,
                "preKeysAvailable", preKeyRepo.countByUserIdAndUsed(user.getId(), false)));
    }

    /** Fetch another user's public bundle — burns one pre-key in the process. */
    @GetMapping("/keys/{userId}")
    public ResponseEntity<KeyBundleDto> fetchKeyBundle(@PathVariable Long userId,
                                                       Authentication authentication) {
        requireUser(authentication);

        Optional<UserIdentityKey> ik = identityRepo.findByUserId(userId);
        if (ik.isEmpty()) return ResponseEntity.notFound().build();

        // Pop the oldest unused pre-key atomically (single-row).
        OneTimePreKey burned = null;
        List<OneTimePreKey> unused = preKeyRepo.findUnused(userId);
        if (!unused.isEmpty()) {
            burned = unused.get(0);
            burned.setUsed(true);
            burned.setConsumedAt(LocalDateTime.now());
            preKeyRepo.save(burned);
        }

        OneTimePreKeyDto preKeyDto = burned == null ? null
                : OneTimePreKeyDto.builder()
                        .keyId(burned.getKeyId())
                        .publicKey(burned.getPublicKeyBase64())
                        .signature(burned.getSignatureBase64())
                        .build();

        return ResponseEntity.ok(KeyBundleDto.builder()
                .userId(userId)
                .keyVersion(ik.get().getKeyVersion())
                .identityPublicKey(ik.get().getIdentityPublicKeyBase64())
                .signingPublicKey(ik.get().getSigningPublicKeyBase64())
                .algorithm(ik.get().getAlgorithm())
                .oneTimePreKey(preKeyDto)
                .build());
    }

    /** How many one-time keys are still available on the server for the caller? */
    @GetMapping("/keys/me/inventory")
    public Map<String, Object> myInventory(Authentication authentication) {
        User user = requireUser(authentication);
        long remaining = preKeyRepo.countByUserIdAndUsed(user.getId(), false);
        boolean hasIdentity = identityRepo.findByUserId(user.getId()).isPresent();
        return Map.of(
                "identityRegistered", hasIdentity,
                "preKeysAvailable", remaining,
                "recommendedTopUpAt", 20,
                "recommendedBundleSize", 100);
    }

    // ==================================================================
    //  helpers
    // ==================================================================

    private User requireUser(Authentication a) {
        User u = currentUser(a);
        if (u == null) throw new SecurityException("Not authenticated");
        return u;
    }

    private User currentUser(Authentication a) {
        if (a == null || a.getName() == null) return null;
        return userRepository.findByEmail(a.getName()).orElse(null);
    }
}
