package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_room_members", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"room_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "room_id")
    private ChatRoom room;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;

    private LocalDateTime joinedAt;

    @PrePersist
    public void prePersist() {
        joinedAt = LocalDateTime.now();
    }
}
