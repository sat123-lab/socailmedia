package com.example.demo.repository;

import com.example.demo.entity.Notification;
import com.example.demo.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository
        extends JpaRepository<Notification, Long> {

    // GET USER NOTIFICATIONS

    List<Notification> findByReceiverOrderByCreatedAtDesc(
            User receiver
    );

    // UNREAD COUNT

    long countByReceiverAndReadFalse(
            User receiver
    );

    // GET UNREAD NOTIFICATIONS

    List<Notification> findByReceiverAndReadFalse(
            User receiver
    );

    List<Notification> findByRelatedIdAndType(
            Long relatedId,
            String type
    );

    void deleteByRelatedIdAndType(
            Long relatedId,
            String type
    );
}