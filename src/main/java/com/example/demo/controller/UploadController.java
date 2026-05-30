package com.example.demo.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Media-upload endpoints used by the Create Camera flow (and the legacy
 * post/story screens).
 *
 * <p>Every response is a JSON object — never a plain text body — so the
 * Flutter client can read the persisted URL with one call:</p>
 *
 * <pre>{@code
 *   { "url": "/uploads/videos/<uuid>_clip.mp4",
 *     "thumbnailUrl": "/uploads/images/<uuid>_thumb.jpg",  // optional
 *     "kind": "video",
 *     "fileName": "<uuid>_clip.mp4" }
 * }</pre>
 *
 * <p>Files are written under {@code ./uploads/{videos,images}/}. The static
 * resource handler in {@link com.example.demo.config.WebConfig} serves them
 * back through {@code /uploads/**}, and {@code SecureMediaController}
 * exposes a signed-URL flavour for sensitive content.</p>
 */
@RestController
@RequestMapping("/upload")
@CrossOrigin("*")
public class UploadController {

    private static final String VIDEO_UPLOAD_DIR =
            System.getProperty("user.dir") + "/uploads/videos/";
    private static final String IMAGE_UPLOAD_DIR =
            System.getProperty("user.dir") + "/uploads/images/";

    /**
     * Unified media upload — accepts an image OR video file (and an
     * optional pre-rendered thumbnail for videos) and returns the
     * persisted URLs in one round-trip. The Create Camera uses this so
     * it doesn't have to choose between {@code /upload/image} and
     * {@code /upload/video} client-side.
     */
    @PostMapping(value = "/media", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> uploadMedia(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "thumbnail", required = false) MultipartFile thumbnail,
            @RequestParam(value = "kind", required = false) String kind
    ) throws IOException {

        boolean isVideo = isVideo(file, kind);
        String savedName = persist(file, isVideo ? VIDEO_UPLOAD_DIR : IMAGE_UPLOAD_DIR);
        String savedUrl = (isVideo ? "/uploads/videos/" : "/uploads/images/") + savedName;

        String thumbUrl = null;
        if (thumbnail != null && !thumbnail.isEmpty()) {
            String thumbName = persist(thumbnail, IMAGE_UPLOAD_DIR);
            thumbUrl = "/uploads/images/" + thumbName;
        }

        Map<String, Object> body = new HashMap<>();
        body.put("url", savedUrl);
        body.put("kind", isVideo ? "video" : "image");
        body.put("fileName", savedName);
        body.put("size", file.getSize());
        if (thumbUrl != null) body.put("thumbnailUrl", thumbUrl);
        return ResponseEntity.ok(body);
    }

    /** Legacy video-only endpoint. JSON response so clients can read the URL. */
    @PostMapping("/video")
    public ResponseEntity<Map<String, Object>> uploadVideo(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        String savedName = persist(file, VIDEO_UPLOAD_DIR);
        return ResponseEntity.ok(Map.of(
                "url", "/uploads/videos/" + savedName,
                "kind", "video",
                "fileName", savedName,
                "size", file.getSize()));
    }

    /** Legacy image-only endpoint. JSON response so clients can read the URL. */
    @PostMapping("/image")
    public ResponseEntity<Map<String, Object>> uploadImage(
            @RequestParam("file") MultipartFile file
    ) throws IOException {
        String savedName = persist(file, IMAGE_UPLOAD_DIR);
        return ResponseEntity.ok(Map.of(
                "url", "/uploads/images/" + savedName,
                "kind", "image",
                "fileName", savedName,
                "size", file.getSize()));
    }

    // ------------------------------------------------------------------
    //  Helpers
    // ------------------------------------------------------------------

    private static String persist(MultipartFile file, String directoryPath) throws IOException {
        File directory = new File(directoryPath);
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Failed to create upload directory: " + directoryPath);
        }
        String safeOriginal = sanitize(file.getOriginalFilename());
        String fileName = UUID.randomUUID() + "_" + safeOriginal;
        File destination = new File(directoryPath + fileName);
        file.transferTo(destination);
        return fileName;
    }

    /** Strip path separators + reserved characters from the client-supplied filename. */
    private static String sanitize(String name) {
        if (name == null || name.isBlank()) return "upload";
        // Drop any path component the client tried to sneak in.
        int sep = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (sep >= 0) name = name.substring(sep + 1);
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static boolean isVideo(MultipartFile file, String hint) {
        if (hint != null && !hint.isBlank()) {
            return "video".equalsIgnoreCase(hint.trim());
        }
        String contentType = file.getContentType();
        if (contentType != null && contentType.startsWith("video/")) return true;
        String name = file.getOriginalFilename();
        if (name == null) return false;
        String lower = name.toLowerCase();
        return lower.endsWith(".mp4") || lower.endsWith(".mov")
                || lower.endsWith(".webm") || lower.endsWith(".m4v")
                || lower.endsWith(".mkv");
    }
}