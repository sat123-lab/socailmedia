package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "room_call_participants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoomCallParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "session_id")
    private RoomCallSession session;

    @ManyToOne
    @JoinColumn(name = "user_id")
    @JsonIgnoreProperties({"password", "posts", "comments", "likes", "followers", "following"})
    private User user;

    private String status;
    private LocalDateTime respondedAt;
}
