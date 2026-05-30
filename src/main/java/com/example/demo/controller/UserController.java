package com.example.demo.controller;

import com.example.demo.dto.PrivacyRequest;
import com.example.demo.dto.UpdateUserRequest;

import com.example.demo.entity.User;

import com.example.demo.repository.UserRepository;
import com.example.demo.service.FileService;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController

@RequestMapping("/user")

@RequiredArgsConstructor

@CrossOrigin("*")

public class UserController {

    private final UserRepository userRepository;
    private final FileService fileService;

    // =========================
    // CURRENT USER
    // =========================

    @GetMapping("/me")

    public User currentUser(
            Authentication authentication
    ) {

        String email =
                authentication.getName();

        return userRepository
                .findByEmail(email)

                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        ));
    }

    // =========================
    // GET USER BY ID
    // =========================

    @GetMapping("/{userId}")

    public User getUserById(
            @PathVariable Long userId
    ) {

        return userRepository
                .findById(userId)

                .orElseThrow(() ->
                        new RuntimeException(
                                "User not found"
                        ));
    }

    // =========================
    // UPDATE USER
    // =========================

    @PutMapping("/update")

    public User updateUser(

            @RequestBody
            UpdateUserRequest request,

            Authentication authentication
    ) {

        String email =
                authentication.getName();

        User user =
                userRepository
                        .findByEmail(email)

                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                ));

        // UPDATE NAME

        if (request.getName() != null) {

            user.setName(
                    request.getName()
            );
        }

        // UPDATE BIO

        if (request.getBio() != null) {

            user.setBio(
                    request.getBio()
            );
        }

        // UPDATE PROFILE IMAGE

        if (request.getProfileImage() != null) {
            String newImage = request.getProfileImage();
            String oldImage = user.getProfileImage();
            user.setProfileImage(newImage);
            if (oldImage != null
                    && !oldImage.isBlank()
                    && !oldImage.equals(newImage)) {
                fileService.deleteFile(oldImage);
            }
        }

        // UPDATE PHONE

        if (request.getPhone()
                != null) {

            user.setPhone(
                    request.getPhone()
            );
        }

        return userRepository
                .save(user);
    }

    // =========================
    // UPLOAD PROFILE IMAGE
    // =========================

    @PostMapping(
            value = "/profile-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public User uploadProfileImage(
            @RequestPart("image") MultipartFile image,
            Authentication authentication
    ) throws IOException {

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String oldImage = user.getProfileImage();

        String path = fileService.uploadFile(image);

        if (path == null || path.isBlank()) {
            throw new RuntimeException("Failed to upload image");
        }

        user.setProfileImage(path);
        User saved = userRepository.save(user);

        if (oldImage != null
                && !oldImage.isBlank()
                && !oldImage.equals(path)) {
            fileService.deleteFile(oldImage);
        }

        return saved;
    }

    // =========================
    // UPDATE PRIVACY
    // =========================

    @PutMapping("/privacy")

    public User updatePrivacy(

            @RequestBody PrivacyRequest request,

            Authentication authentication
    ) {

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setPrivateAccount(request.isPrivateAccount());

        return userRepository.save(user);
    }

    // =========================
    // SEARCH USERS
    // =========================

    @GetMapping("/search")

    public List<User> searchUsers(

            @RequestParam
            String keyword
    ) {

        return userRepository
                .findByNameContainingIgnoreCase(
                        keyword
                );
    }
}