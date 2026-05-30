package com.example.demo.controller;

import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import com.example.demo.repository.NotificationRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@CrossOrigin("*")
public class NotificationController {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    // GET ALL NOTIFICATIONS

    @GetMapping
    public List<Notification> getNotifications(
            Authentication authentication
    ) {

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return notificationRepository
                .findByReceiverOrderByCreatedAtDesc(user);
    }

    // GET UNREAD COUNT

    @GetMapping("/unread-count")
    public long unreadCount(
            Authentication authentication
    ) {

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return notificationRepository
                .countByReceiverAndReadFalse(user);
    }

    // MARK AS READ

    @PutMapping("/{notificationId}/read")
    public String markAsRead(
            @PathVariable Long notificationId
    ) {

        Notification notification =
                notificationRepository.findById(notificationId)
                        .orElseThrow(() ->
                                new RuntimeException("Notification not found"));

        notification.setRead(true);

        notificationRepository.save(notification);

        return "Notification marked as read";
    }
}