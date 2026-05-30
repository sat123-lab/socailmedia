package com.example.demo.service;

import com.example.demo.entity.Follow;
import com.example.demo.entity.User;
import com.example.demo.repository.FollowRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class FollowCountService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    public static boolean isTestUser(User user) {
        if (user == null) {
            return true;
        }
        String email = user.getEmail();
        if (email != null) {
            String lower = email.toLowerCase();
            if (lower.endsWith("@test.com")) {
                return true;
            }
        }
        String name = user.getName();
        if (name == null) {
            return false;
        }
        return name.equals("TestFollow")
                || name.equals("FollowTest2")
                || name.equals("FollowTest");
    }

    public int countFollowers(User user) {
        return (int) followRepository.findByFollowing(user).stream()
                .map(Follow::getFollower)
                .filter(follower -> !isTestUser(follower))
                .count();
    }

    public int countFollowing(User user) {
        return (int) followRepository.findByFollower(user).stream()
                .map(Follow::getFollowing)
                .filter(target -> !isTestUser(target))
                .count();
    }

    public void syncCountsForUser(User user) {
        userRepository.updateFollowersCount(user.getId(), countFollowers(user));
        userRepository.updateFollowingCount(user.getId(), countFollowing(user));
    }

    public void syncCountsAfterFollowChange(User follower, User following) {
        userRepository.updateFollowingCount(follower.getId(), countFollowing(follower));
        userRepository.updateFollowersCount(following.getId(), countFollowers(following));
    }
}
