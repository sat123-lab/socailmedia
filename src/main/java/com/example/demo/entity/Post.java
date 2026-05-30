package com.example.demo.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "posts")

public class Post {

    @Id
    @GeneratedValue(
            strategy =
            GenerationType.IDENTITY
    )

    private Long id;

    // CAPTION

    @Column(length = 2000)

    private String caption;

    // IMAGE URL

    private String imageUrl;

    // VIDEO URL

    private String videoUrl;

    // THUMBNAIL URL

    private String thumbnailUrl;

    // POST TYPE

    private String type;

    // LIKES COUNT

    private int likesCount = 0;

    // COMMENTS COUNT

    private int commentsCount = 0;

    // CREATED TIME

    private LocalDateTime createdAt =
            LocalDateTime.now();

    // USER

    @ManyToOne(fetch = FetchType.EAGER)

    @JoinColumn(name = "user_id")

    @JsonIgnoreProperties({

            "password",
            "posts",
            "comments",
            "likes"
    })

    private User user;

    // COMMENTS

    @OneToMany(

            mappedBy = "post",

            cascade = CascadeType.ALL,

            orphanRemoval = true
    )

    @JsonManagedReference

    private List<Comment> comments =
            new ArrayList<>();

    // LIKES

    @JsonIgnore

    @OneToMany(

            mappedBy = "post",

            cascade = CascadeType.ALL,

            orphanRemoval = true
    )

    private List<Like> likes =
            new ArrayList<>();

    // CONSTRUCTORS

    public Post() {
    }

    // GETTERS & SETTERS

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCaption() {
        return caption;
    }

    public void setCaption(
            String caption
    ) {
        this.caption = caption;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(
            String imageUrl
    ) {
        this.imageUrl = imageUrl;
    }

    public String getVideoUrl() {
        return videoUrl;
    }

    public void setVideoUrl(
            String videoUrl
    ) {
        this.videoUrl = videoUrl;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(
            String thumbnailUrl
    ) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getType() {
        return type;
    }

    public void setType(
            String type
    ) {
        this.type = type;
    }

    public int getLikesCount() {
        return likesCount;
    }

    public void setLikesCount(
            int likesCount
    ) {
        this.likesCount = likesCount;
    }

    public int getCommentsCount() {
        return commentsCount;
    }

    public void setCommentsCount(
            int commentsCount
    ) {
        this.commentsCount = commentsCount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(
            LocalDateTime createdAt
    ) {
        this.createdAt = createdAt;
    }

    public User getUser() {
        return user;
    }

    public void setUser(
            User user
    ) {
        this.user = user;
    }

    public List<Comment> getComments() {
        return comments;
    }

    public void setComments(
            List<Comment> comments
    ) {
        this.comments = comments;
    }

    public List<Like> getLikes() {
        return likes;
    }

    public void setLikes(
            List<Like> likes
    ) {
        this.likes = likes;
    }

    // AUTO CREATE TIME

    @PrePersist

    public void prePersist() {

        if (createdAt == null) {

            createdAt =
                    LocalDateTime.now();
        }
    }
}