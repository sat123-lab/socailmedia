package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;

@Entity
@Table(name = "likes")
public class Like {

    @Id
    @GeneratedValue(strategy =
    GenerationType.IDENTITY)

    private Long id;

    // USER

    @ManyToOne

    @JoinColumn(name = "user_id")

    private User user;

    // POST

    @ManyToOne

    @JoinColumn(name = "post_id")

    @JsonBackReference

    private Post post;

    public Like() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Post getPost() {
        return post;
    }

    public void setPost(Post post) {
        this.post = post;
    }
}