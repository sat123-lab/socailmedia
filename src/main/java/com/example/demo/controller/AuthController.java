package com.example.demo.controller;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.FirebaseAuthRequest;
import com.example.demo.dto.GoogleAuthRequest;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.PhoneOtpRequest;
import com.example.demo.dto.PhoneVerifyRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@CrossOrigin("*")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    // =========================
    // REGISTER API
    // =========================

    @PostMapping("/register")
    public String register(@RequestBody RegisterRequest request,
                            HttpServletRequest http) {
        return authService.register(request, clientIp(http),
                http.getHeader("User-Agent"),
                http.getHeader("X-Device-Id"));
    }

    // =========================
    // LOGIN API
    // =========================

    @PostMapping("/login")
    public AuthResponse login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    // =========================
    // FIREBASE (Google sign-in + Phone OTP)
    // =========================

    @PostMapping("/firebase")
    public AuthResponse loginWithFirebase(@RequestBody FirebaseAuthRequest request,
                                           HttpServletRequest http) {
        return authService.loginWithFirebase(request, clientIp(http),
                http.getHeader("User-Agent"),
                http.getHeader("X-Device-Id"));
    }

    // =========================
    // GOOGLE SIGN-IN (legacy trust-based, kept for fallback)
    // =========================

    @PostMapping("/google")
    public AuthResponse loginWithGoogle(@RequestBody GoogleAuthRequest request,
                                         HttpServletRequest http) {
        return authService.loginWithGoogle(request, clientIp(http),
                http.getHeader("User-Agent"),
                http.getHeader("X-Device-Id"));
    }

    // =========================
    // PHONE OTP - SEND
    // =========================

    @PostMapping("/phone/send-otp")
    public Map<String, String> sendPhoneOtp(@RequestBody PhoneOtpRequest request) {
        String result = authService.sendPhoneOtp(request);
        return Map.of("status", "ok", "message", result);
    }

    // =========================
    // PHONE OTP - VERIFY
    // =========================

    @PostMapping("/phone/verify")
    public AuthResponse verifyPhoneOtp(@RequestBody PhoneVerifyRequest request,
                                        HttpServletRequest http) {
        return authService.verifyPhoneOtp(request, clientIp(http),
                http.getHeader("User-Agent"),
                http.getHeader("X-Device-Id"));
    }

    // =========================
    // CHECK AUTH USER
    // =========================

    @GetMapping("/me")
    public User getCurrentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    // =========================
    // AUTH CHECK
    // =========================

    @GetMapping("/check")
    public String checkAuth() {
        return "Authenticated";
    }

    // ------------------------------------------------------------------

    /**
     * Resolve the real client IP. Trust the leftmost X-Forwarded-For
     * value when it's set — production runs behind a CDN/load balancer.
     * IPs are only ever hashed downstream, so a spoofed header just
     * lands in a different fraud bucket; never raw-stored.
     */
    private static String clientIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma < 0 ? xff : xff.substring(0, comma)).trim();
        }
        return req.getRemoteAddr();
    }
}
