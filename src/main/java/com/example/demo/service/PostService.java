package com.example.demo.service;

import com.example.demo.dto.PostRequest;
import com.example.demo.entity.Post;
import com.example.demo.entity.User;
import com.example.demo.repository.FollowRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.UserRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class PostService {

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FollowRepository followRepository;

    @Autowired
    private FileService fileService;

    /**
     * Optional — used to flip a SIGNED_UP referral to ACTIVATED when
     * the referee creates their first post. Keeps the funnel honest;
     * the future rewards engine keys off this transition. Wrapped in a
     * try/catch in {@link #createPost} so it can never block content
     * creation.
     */
    @Autowired(required = false)
    private ReferralService referralService;

    // CREATE POST

    public Post createPost(

            PostRequest request,
            String email,

            MultipartFile image,
            MultipartFile video

    ) throws Exception {

        User user =
                userRepository.findByEmail(email)

                        .orElseThrow(() ->
                                new RuntimeException(
                                        "User not found"
                                )
                        );

        Post post = new Post();

        // CAPTION

        post.setCaption(
                request.getCaption()
        );

        // USER

        post.setUser(user);

        // CREATED TIME

        post.setCreatedAt(
                LocalDateTime.now()
        );

        // IMAGE UPLOAD

        if (image != null &&
                !image.isEmpty()) {

            String imageUrl =
                    fileService.uploadFile(
                            image
                    );

            post.setImageUrl(imageUrl);

            post.setType("image");
        }

        // VIDEO UPLOAD

        if (video != null &&
                !video.isEmpty()) {

            String videoUrl =
                    fileService.uploadFile(
                            video
                    );

            post.setVideoUrl(videoUrl);

            post.setType("video");
        }

        // THUMBNAIL

        if (request.getThumbnailUrl()
                != null) {

            post.setThumbnailUrl(
                    request.getThumbnailUrl()
            );
        }

        Post saved = postRepository.save(post);
        // Best-effort referral activation. If the user came in via a
        // referral, their first post moves the funnel SIGNED_UP -> ACTIVATED.
        // Idempotent: ReferralService.markActivated only flips on the first call.
        if (referralService != null && user.getReferredByUserId() != null) {
            try {
                referralService.markActivated(user.getId());
            } catch (Exception ignored) {
                // Funnel telemetry is never allowed to break a real post.
            }
        }
        return saved;
    }

    private User resolveViewer(String viewerEmail) {
        if (viewerEmail == null || viewerEmail.isBlank()) {
            return null;
        }
        return userRepository.findByEmail(viewerEmail).orElse(null);
    }

    private boolean canViewUserContent(User author, User viewer) {
        if (author == null) {
            return false;
        }
        if (!author.isPrivateAccount()) {
            return true;
        }
        if (viewer == null) {
            return false;
        }
        if (viewer.getId().equals(author.getId())) {
            return true;
        }
        return followRepository.existsByFollowerAndFollowing(viewer, author);
    }

    private List<Post> filterVisiblePosts(List<Post> posts, String viewerEmail) {
        User viewer = resolveViewer(viewerEmail);
        return posts.stream()
                .filter(post -> canViewUserContent(post.getUser(), viewer))
                .toList();
    }

    // GET FEED

    public List<Post> getFeed(String viewerEmail) {
        return filterVisiblePosts(
                postRepository.findAllByOrderByCreatedAtDesc(),
                viewerEmail
        );
    }

    public List<Post> getFeedPage(int page, int size, String viewerEmail) {
        return filterVisiblePosts(
                postRepository.findAllByOrderByCreatedAtDesc(
                        PageRequest.of(page, size)
                ),
                viewerEmail
        );
    }

    // GET SINGLE POST

    public Post getPostById(
            Long postId
    ) {

        return postRepository.findById(postId)

                .orElseThrow(() ->
                        new RuntimeException(
                                "Post not found"
                        )
                );
    }

    // GET POSTS BY USER

    public long countPostsByUser(Long userId) {
        return postRepository.countByUser_Id(userId);
    }

    public List<Post> getPostsByUser(
            Long userId,
            String viewerEmail
    ) {

        User target = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<Post> posts = postRepository
                .findByUserIdOrderByCreatedAtDesc(userId);

        if (!target.isPrivateAccount()) {
            return posts;
        }

        if (viewerEmail == null || viewerEmail.isBlank()) {
            return List.of();
        }

        User viewer = userRepository.findByEmail(viewerEmail)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (viewer.getId().equals(target.getId())) {
            return posts;
        }

        if (followRepository.existsByFollowerAndFollowing(viewer, target)) {
            return posts;
        }

        return List.of();
    }

    // DELETE POST

    public void deletePost(

            Long postId,
            String email

    ) {

        Post post =
                postRepository.findById(postId)

                        .orElseThrow(() ->
                                new RuntimeException(
                                        "Post not found"
                                )
                        );

        // SECURITY CHECK

        if (!post.getUser()
                .getEmail()
                .equals(email)) {

            throw new RuntimeException(
                    "You can delete only your posts"
            );
        }

        postRepository.delete(post);
    }

    public List<Post> getReels(String viewerEmail) {
        return filterVisiblePosts(
                postRepository.findByVideoUrlIsNotNullOrderByCreatedAtDesc(),
                viewerEmail
        );
    }

    public List<Post> searchPosts(String query, String viewerEmail) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return filterVisiblePosts(
                postRepository.findByCaptionContainingIgnoreCaseOrderByCreatedAtDesc(
                        query.trim()
                ),
                viewerEmail
        );
    }

    public List<Post> searchReels(String query, String viewerEmail) {
        if (query == null || query.isBlank()) {
            return getReels(viewerEmail);
        }
        return filterVisiblePosts(
                postRepository
                        .findByVideoUrlIsNotNullAndCaptionContainingIgnoreCaseOrderByCreatedAtDesc(
                                query.trim()
                        ),
                viewerEmail
        );
    }
}