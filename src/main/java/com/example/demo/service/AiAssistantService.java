package com.example.demo.service;

import com.example.demo.dto.AiResponseDto;
import com.example.demo.dto.AiStickerDto;
import com.example.demo.entity.Post;
import com.example.demo.entity.User;
import com.example.demo.repository.FollowRepository;
import com.example.demo.repository.PostRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiAssistantService {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final FollowRepository followRepository;

    private User me(Authentication auth) {
        return userRepository.findByEmail(auth.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    public AiResponseDto suggestVideos(Authentication auth) {
        User user = me(auth);
        List<String> items = postRepository.findAll().stream()
                .filter(p -> p.getVideoUrl() != null && !p.getVideoUrl().isBlank())
                .sorted(Comparator.comparing(Post::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(8)
                .map(p -> {
                    String cap = p.getCaption() != null ? p.getCaption() : "Reel";
                    return cap.length() > 60 ? cap.substring(0, 57) + "..." : cap;
                })
                .collect(Collectors.toList());

        if (items.isEmpty()) {
            items = List.of("Trending comedy clips", "Travel reels", "Music snippets");
        }

        return AiResponseDto.builder()
                .reply("Based on what's popular on Vibe U, you might enjoy these videos:")
                .items(items)
                .build();
    }

    public AiResponseDto suggestFriends(Authentication auth) {
        User user = me(auth);
        Set<Long> following = followRepository.findByFollower(user).stream()
                .map(f -> f.getFollowing().getId())
                .collect(Collectors.toSet());
        following.add(user.getId());

        List<String> items = userRepository.findAll().stream()
                .filter(u -> !following.contains(u.getId()))
                .limit(10)
                .map(u -> u.getName() + (u.getBio() != null && !u.getBio().isBlank()
                        ? " — " + truncate(u.getBio(), 40) : ""))
                .collect(Collectors.toList());

        return AiResponseDto.builder()
                .reply(items.isEmpty()
                        ? "You're connected with most active users. Search to find more!"
                        : "People you may want to follow:")
                .items(items)
                .build();
    }

    public AiResponseDto suggestStickers(Authentication auth) {
        List<AiStickerDto> stickers = List.of(
                sticker("😂", "LOL", "Haha that's epic!"),
                sticker("🔥", "Fire", "This is fire!"),
                sticker("❤️", "Love", "Love this!"),
                sticker("👏", "Clap", "Well done!"),
                sticker("🎉", "Party", "Let's celebrate!"),
                sticker("😍", "Wow", "So good!")
        );
        return AiResponseDto.builder()
                .reply("Quick stickers for your chats:")
                .stickers(stickers)
                .build();
    }

    public AiResponseDto ask(String prompt, Authentication auth) {
        User user = me(auth);
        String p = prompt != null ? prompt.trim().toLowerCase() : "";
        String reply;
        List<String> items = new ArrayList<>();

        if (p.contains("friend") || p.contains("follow")) {
            return suggestFriends(auth);
        }
        if (p.contains("video") || p.contains("reel")) {
            return suggestVideos(auth);
        }
        if (p.contains("sticker")) {
            return suggestStickers(auth);
        }
        if (p.contains("post") || p.contains("caption")) {
            reply = "Try these caption ideas:";
            items = List.of(
                    "Good vibes only ✨",
                    "Making memories 📸",
                    "Another day, another adventure",
                    "Feeling grateful today 🙏"
            );
        } else if (p.isEmpty()) {
            reply = "Ask me about video ideas, friends to follow, stickers, or caption help!";
        } else {
            reply = "Here's what I can suggest for \"" + prompt.trim() + "\": stay authentic, use a short hook in the first line, and add one relevant hashtag. Want video or friend suggestions? Just ask!";
        }

        return AiResponseDto.builder().reply(reply).items(items).build();
    }

    public AiResponseDto stickerForSituation(String situation, Authentication auth) {
        me(auth);
        String s = situation != null ? situation.toLowerCase() : "";
        List<AiStickerDto> stickers = new ArrayList<>();

        if (s.contains("birthday") || s.contains("bday")) {
            stickers.add(sticker("🎂", "Birthday", "Happy Birthday! 🎉"));
            stickers.add(sticker("🥳", "Celebrate", "Have an amazing day!"));
        } else if (s.contains("love") || s.contains("miss")) {
            stickers.add(sticker("❤️", "Love", "Sending love!"));
            stickers.add(sticker("🥰", "Miss you", "Miss you lots!"));
        } else if (s.contains("sorry") || s.contains("sad")) {
            stickers.add(sticker("🙏", "Sorry", "I'm really sorry"));
            stickers.add(sticker("🫂", "Hug", "Here for you"));
        } else if (s.contains("funny") || s.contains("comedy") || s.contains("joke")) {
            stickers.add(sticker("😂", "LOL", "Can't stop laughing!"));
            stickers.add(sticker("🤣", "ROFL", "You're hilarious!"));
        } else if (s.contains("congrat") || s.contains("win")) {
            stickers.add(sticker("🏆", "Winner", "Congratulations!"));
            stickers.add(sticker("🎊", "Congrats", "So proud of you!"));
        } else {
            stickers.add(sticker("✨", "Vibes", "Good vibes!"));
            stickers.add(sticker("👍", "OK", "Sounds good!"));
            stickers.add(sticker("💬", "Chat", "Let's talk!"));
        }

        return AiResponseDto.builder()
                .reply("Stickers for: " + (situation != null && !situation.isBlank() ? situation : "your moment"))
                .stickers(stickers)
                .build();
    }

    private static AiStickerDto sticker(String emoji, String label, String text) {
        return AiStickerDto.builder().emoji(emoji).label(label).text(text).build();
    }

    private static String truncate(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max - 1) + "…";
    }
}
