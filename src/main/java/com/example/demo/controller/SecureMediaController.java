package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.MediaTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

/**
 * Anti-scraping media serving.
 *
 * <ul>
 *   <li>{@code GET /media/signed-url?path=foo/bar.jpg} → returns a token + url
 *       valid for ~5 minutes. Caller must be authenticated.</li>
 *   <li>{@code GET /media/serve/{token}/{*path}} → streams the file. Token is
 *       cryptographically bound to {@code path}, so it cannot be reused for
 *       any other resource. Once it expires, scrapers cannot fetch anything.</li>
 * </ul>
 *
 * <p>The legacy {@code /uploads/**} static serving still works for backward
 * compatibility while clients migrate. After migration, remove it from
 * {@code SecurityConfig} to fully lock down the bucket.</p>
 */
@RestController
@RequestMapping("/media")
@RequiredArgsConstructor
public class SecureMediaController {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    private final MediaTokenService mediaTokens;
    private final UserRepository userRepository;

    @GetMapping("/signed-url")
    public ResponseEntity<Map<String, Object>> signedUrl(
            @RequestParam String path,
            @RequestParam(required = false) Long ttl,
            Authentication authentication) {

        Long userId = currentUserId(authentication);
        String safe = normalizePath(path);
        String token = mediaTokens.issueToken(safe, userId, ttl);
        long ttlSec = (ttl == null || ttl <= 0) ? MediaTokenService.DEFAULT_TTL_SECONDS : ttl;

        return ResponseEntity.ok(Map.of(
                "token", token,
                "path", safe,
                "url", "/media/serve/" + token + "/" + safe,
                "expiresIn", ttlSec
        ));
    }

    @GetMapping("/serve/{token}/**")
    public ResponseEntity<Resource> serve(@PathVariable String token,
                                          jakarta.servlet.http.HttpServletRequest request) throws IOException {

        // Everything after `/media/serve/{token}/` is the requested file path.
        String full = request.getRequestURI();
        String marker = "/media/serve/" + token + "/";
        int idx = full.indexOf(marker);
        if (idx < 0) return ResponseEntity.badRequest().build();
        String requested = full.substring(idx + marker.length());

        Optional<?> claims = mediaTokens.validate(token, requested);
        if (claims.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        Path file = Paths.get(uploadDir).resolve(requested).normalize();
        if (!file.startsWith(Paths.get(uploadDir).normalize())) {
            // Path traversal attempt.
            return ResponseEntity.status(403).build();
        }
        if (!Files.exists(file) || Files.isDirectory(file)) {
            return ResponseEntity.notFound().build();
        }

        String contentType = URLConnection.guessContentTypeFromName(file.getFileName().toString());
        if (contentType == null) contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;

        Resource body = new FileSystemResource(file);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=60")
                .header("X-Content-Type-Options", "nosniff")
                .body(body);
    }

    // ------------------------------------------------------------------
    private Long currentUserId(Authentication a) {
        if (a == null) return null;
        return userRepository.findByEmail(a.getName()).map(User::getId).orElse(null);
    }

    private static String normalizePath(String raw) {
        if (raw == null) return "";
        String s = raw.replace("\\", "/");
        if (s.startsWith("/")) s = s.substring(1);
        if (s.startsWith("uploads/")) s = s.substring("uploads/".length());
        return s;
    }
}
