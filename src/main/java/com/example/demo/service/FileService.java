package com.example.demo.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Service
public class FileService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    public String uploadFile(
            MultipartFile file
    ) throws IOException {

        if (file.isEmpty()) {

            return null;
        }

        String fileName =
                UUID.randomUUID()
                        + "_"
                        + file.getOriginalFilename();

        Path uploadPath =
                Paths.get(uploadDir);

        if (!Files.exists(uploadPath)) {

            Files.createDirectories(uploadPath);
        }

        Path filePath =
                uploadPath.resolve(fileName);

        Files.copy(
                file.getInputStream(),
                filePath,
                StandardCopyOption.REPLACE_EXISTING
        );

        return "/uploads/" + fileName;
    }

    /**
     * Removes a previously uploaded file referenced by its public path (e.g. /uploads/abc.jpg).
     */
    public void deleteFile(String publicPath) {
        if (publicPath == null || publicPath.isBlank()) {
            return;
        }
        if (!publicPath.startsWith("/uploads/")) {
            return;
        }

        String fileName = publicPath.substring("/uploads/".length());
        if (fileName.isBlank()
                || fileName.contains("..")
                || fileName.contains("/")
                || fileName.contains("\\")) {
            return;
        }

        Path filePath = Paths.get(uploadDir).resolve(fileName).normalize();
        Path basePath = Paths.get(uploadDir).toAbsolutePath().normalize();
        if (!filePath.toAbsolutePath().normalize().startsWith(basePath)) {
            return;
        }

        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // Do not fail the main request if cleanup fails
        }
    }
}