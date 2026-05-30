package com.example.demo.service;

import com.example.demo.dto.AuthResponse;
import com.example.demo.dto.FirebaseAuthRequest;
import com.example.demo.dto.GoogleAuthRequest;
import com.example.demo.dto.LoginRequest;
import com.example.demo.dto.PhoneOtpRequest;
import com.example.demo.dto.PhoneVerifyRequest;
import com.example.demo.dto.RegisterRequest;
import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.security.AuditLogService;
import com.example.demo.security.BruteForceProtectionService;
import com.example.demo.security.JwtUtil;
import com.example.demo.security.SecurityProperties;
import com.example.demo.security.TokenManager;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;

    // Security upgrades wired in for refresh tokens + brute-force protection.
    // All are optional — flows still compile if individual beans fail to inject only
    // because they're required by RequiredArgsConstructor, but every one of them is
    // unconditionally registered by Spring.
    private final TokenManager tokenManager;
    private final BruteForceProtectionService bruteForce;
    private final AuditLogService auditLog;
    private final SecurityProperties securityProperties;
    private final com.example.demo.security.SecurityMonitorService securityMonitor;

    /**
     * Referral attribution — every new-user branch funnels its
     * {@code referralCode} (when present) through this service so a
     * single signup can never be credited twice. Optional dependency
     * (the bean is unconditionally registered, but the auth flow stays
     * functional even if attribution silently no-ops).
     */
    private final ReferralService referralService;

    public String register(RegisterRequest request) {
        return register(request, null, null, null);
    }

    /**
     * Overload that carries the request-side context needed by the
     * referral fraud heuristics (IP, user-agent). The plain
     * {@link #register(RegisterRequest)} stays for any caller that
     * doesn't have access to the servlet request.
     */
    public String register(RegisterRequest request, String ipAddress,
                            String userAgent, String deviceId) {
        if (request.getEmail() != null &&
                userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }
        if (request.getPhone() != null && !request.getPhone().isBlank() &&
                userRepository.existsByPhone(request.getPhone())) {
            throw new RuntimeException("Phone already registered");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .password(passwordEncoder.encode(request.getPassword()))
                .authProvider(provider(request.getAuthProvider(), "EMAIL"))
                .build();

        user = userRepository.save(user);
        attributeReferral(request.getReferralCode(), user, ipAddress,
                userAgent, deviceId == null ? request.getDeviceId() : deviceId,
                "EMAIL");
        return "User Registered Successfully";
    }

    public AuthResponse login(LoginRequest request) {
        String identifier = request.getEmail() == null ? "" : request.getEmail().trim().toLowerCase();

        // Brute-force gate — refuse before touching the password hash.
        if (bruteForce.isLocked(identifier)) {
            auditLog.record(AuditLogService.LOGIN_BLOCKED, null, identifier);
            throw new RuntimeException(
                    "Too many failed attempts. Try again in a few minutes.");
        }

        User user = userRepository.findByEmail(request.getEmail()).orElse(null);
        if (user == null) {
            bruteForce.recordFailure(identifier);
            auditLog.record(AuditLogService.LOGIN_FAILURE, null, identifier + " · unknown user");
            throw new RuntimeException("User not found");
        }

        if (user.getPassword() == null) {
            throw new RuntimeException(
                    "This account was created with " + safeProvider(user) +
                            ". Use that to sign in."
            );
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            bruteForce.recordFailure(identifier);
            auditLog.record(AuditLogService.LOGIN_FAILURE, user.getId(), "bad password");
            securityMonitor.onLoginFailure(identifier);
            throw new RuntimeException("Invalid password");
        }

        bruteForce.recordSuccess(identifier);
        auditLog.record(AuditLogService.LOGIN_SUCCESS, user.getId(), "password");
        return issueTokens(user, "Login Successful");
    }

    /* ====================== GOOGLE SIGN-IN ====================== */

    public AuthResponse loginWithGoogle(GoogleAuthRequest request) {
        return loginWithGoogle(request, null, null, null);
    }

    @Transactional
    public AuthResponse loginWithGoogle(GoogleAuthRequest request,
                                         String ipAddress, String userAgent,
                                         String deviceId) {
        if (request.getEmail() == null || request.getEmail().isBlank()) {
            throw new RuntimeException("Google account email is required");
        }

        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email).orElse(null);
        boolean isNewUser = user == null;

        if (user == null) {
            user = User.builder()
                    .name(safeName(request.getName(), email))
                    .email(email)
                    .googleId(request.getGoogleId())
                    .profileImage(request.getProfileImage())
                    .authProvider("GOOGLE")
                    .build();
            user = userRepository.save(user);
        } else {
            boolean changed = false;
            if (user.getGoogleId() == null && request.getGoogleId() != null) {
                user.setGoogleId(request.getGoogleId());
                changed = true;
            }
            if ((user.getProfileImage() == null || user.getProfileImage().isBlank())
                    && request.getProfileImage() != null) {
                user.setProfileImage(request.getProfileImage());
                changed = true;
            }
            if (user.getAuthProvider() == null) {
                user.setAuthProvider("GOOGLE");
                changed = true;
            }
            if (changed) {
                userRepository.save(user);
            }
        }

        if (isNewUser) {
            attributeReferral(request.getReferralCode(), user, ipAddress,
                    userAgent,
                    deviceId == null ? request.getDeviceId() : deviceId,
                    "GOOGLE");
        }
        auditLog.record(AuditLogService.LOGIN_SUCCESS, user.getId(), "google");
        return issueTokens(user, "Google login successful");
    }

    /* ====================== PHONE OTP ====================== */

    public String sendPhoneOtp(PhoneOtpRequest request) {
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new RuntimeException("Phone number is required");
        }
        otpService.issueOtp(request.getPhone().trim());
        return "OTP sent";
    }

    public AuthResponse verifyPhoneOtp(PhoneVerifyRequest request) {
        return verifyPhoneOtp(request, null, null, null);
    }

    @Transactional
    public AuthResponse verifyPhoneOtp(PhoneVerifyRequest request,
                                        String ipAddress, String userAgent,
                                        String deviceId) {
        if (request.getPhone() == null || request.getPhone().isBlank()) {
            throw new RuntimeException("Phone number is required");
        }
        if (request.getOtp() == null || request.getOtp().isBlank()) {
            throw new RuntimeException("OTP is required");
        }

        String phone = request.getPhone().trim();
        if (!otpService.verify(phone, request.getOtp())) {
            throw new RuntimeException("Invalid or expired OTP");
        }

        Optional<User> existing = userRepository.findByPhone(phone);
        User user;
        boolean isNewUser = existing.isEmpty();
        if (existing.isPresent()) {
            user = existing.get();
            if (user.getAuthProvider() == null) {
                user.setAuthProvider("PHONE");
                userRepository.save(user);
            }
        } else {
            String name = safeName(request.getName(), phone);
            String pseudoEmail = phone.replaceAll("[^0-9]", "") + "@phone.local";
            user = User.builder()
                    .name(name)
                    .phone(phone)
                    .email(pseudoEmail)
                    .authProvider("PHONE")
                    .build();
            user = userRepository.save(user);
        }

        if (isNewUser) {
            attributeReferral(request.getReferralCode(), user, ipAddress,
                    userAgent,
                    deviceId == null ? request.getDeviceId() : deviceId,
                    "PHONE");
        }
        auditLog.record(AuditLogService.LOGIN_SUCCESS, user.getId(), "phone-otp");
        return issueTokens(user, "Phone login successful");
    }

    /* ====================== FIREBASE (Google + Phone) ====================== */

    /**
     * Verifies a Firebase ID token (issued for Google sign-in or Phone OTP
     * verification on the client) and creates / loads the matching user.
     */
    public AuthResponse loginWithFirebase(FirebaseAuthRequest request) {
        return loginWithFirebase(request, null, null, null);
    }

    @Transactional
    public AuthResponse loginWithFirebase(FirebaseAuthRequest request,
                                           String ipAddress, String userAgent,
                                           String deviceId) {
        if (request.getIdToken() == null || request.getIdToken().isBlank()) {
            throw new RuntimeException("Firebase ID token is required");
        }
        if (FirebaseApp.getApps().isEmpty()) {
            throw new RuntimeException(
                    "Firebase is not configured on the server. " +
                            "Add the service-account JSON and restart."
            );
        }

        FirebaseToken decoded;
        try {
            decoded = FirebaseAuth.getInstance().verifyIdToken(request.getIdToken());
        } catch (FirebaseAuthException e) {
            throw new RuntimeException("Invalid Firebase token: " + e.getMessage());
        }

        String uid = decoded.getUid();
        String email = lower(decoded.getEmail());
        String phoneClaim = stringClaim(decoded, "phone_number");
        String providerId = stringClaim(decoded, "sign_in_provider");
        String picture = decoded.getPicture();
        String displayName = displayName(decoded.getName(), request.getName(), email, phoneClaim);

        User user = findExistingUser(uid, email, phoneClaim);
        boolean isNewUser = user == null;

        if (user == null) {
            user = User.builder()
                    .name(displayName)
                    .email(email != null ? email : syntheticEmail(phoneClaim, uid))
                    .phone(phoneClaim)
                    .googleId(uid)
                    .profileImage(picture)
                    .authProvider(provider(providerId))
                    .build();
            user = userRepository.save(user);
        } else {
            boolean dirty = false;
            if (user.getGoogleId() == null) {
                user.setGoogleId(uid);
                dirty = true;
            }
            if (user.getPhone() == null && phoneClaim != null) {
                user.setPhone(phoneClaim);
                dirty = true;
            }
            if ((user.getProfileImage() == null || user.getProfileImage().isBlank())
                    && picture != null) {
                user.setProfileImage(picture);
                dirty = true;
            }
            if (user.getAuthProvider() == null) {
                user.setAuthProvider(provider(providerId));
                dirty = true;
            }
            if (dirty) userRepository.save(user);
        }

        if (isNewUser) {
            attributeReferral(request.getReferralCode(), user, ipAddress,
                    userAgent,
                    deviceId == null ? request.getDeviceId() : deviceId,
                    "FIREBASE");
        }
        auditLog.record(AuditLogService.LOGIN_SUCCESS, user.getId(), "firebase:" + providerId);
        return issueTokens(user, "Firebase login successful");
    }

    /**
     * Best-effort referral attribution shared by every signup branch.
     * Failures are logged but never block the signup itself — referral
     * is a side-channel, not a hard dependency.
     */
    private void attributeReferral(String referralCode, User user,
                                    String ipAddress, String userAgent,
                                    String deviceId, String source) {
        if (referralCode == null || referralCode.isBlank()) return;
        try {
            referralService.attributeSignup(referralCode, user, ipAddress,
                    deviceId, userAgent, source);
        } catch (Exception ex) {
            // Defensive — never let a referral edge case roll back a
            // legitimate signup. The referral row is recoverable; the
            // user account is not.
            org.slf4j.LoggerFactory.getLogger(AuthService.class)
                    .warn("Referral attribution failed for user {}: {}",
                            user.getId(), ex.getMessage());
        }
    }

    // ------------------------------------------------------------------
    //  Token issuance helper — every login path funnels through here so refresh
    //  tokens and the new HS512 access tokens are issued consistently.
    // ------------------------------------------------------------------

    private AuthResponse issueTokens(User user, String message) {
        // Keep the legacy HS256 token for clients that haven't been upgraded yet —
        // JwtFilter accepts both formats during the transition.
        String legacy = jwtUtil.generateToken(user.getEmail());
        String access = tokenManager.issueAccessToken(user);
        String refresh = tokenManager.issueRefreshToken(user, null);

        return AuthResponse.builder()
                .token(access != null ? access : legacy)
                .message(message)
                .refreshToken(refresh)
                .accessExpiresIn(securityProperties.getJwt().getAccessTtlSeconds())
                .build();
    }

    private User findExistingUser(String uid, String email, String phone) {
        User user = userRepository.findByEmail(email == null ? "" : email).orElse(null);
        if (user != null) return user;
        if (phone != null && !phone.isBlank()) {
            user = userRepository.findByPhone(phone).orElse(null);
            if (user != null) return user;
        }
        return null;
    }

    private String stringClaim(FirebaseToken token, String name) {
        Object value = token.getClaims().get(name);
        if (value instanceof String s && !s.isBlank()) return s;
        if ("sign_in_provider".equals(name)) {
            Object firebase = token.getClaims().get("firebase");
            if (firebase instanceof Map<?, ?> map) {
                Object v = map.get("sign_in_provider");
                if (v instanceof String s2) return s2;
            }
        }
        return null;
    }

    private String displayName(String fromToken, String fromRequest, String email, String phone) {
        if (fromRequest != null && !fromRequest.isBlank()) return fromRequest.trim();
        if (fromToken != null && !fromToken.isBlank()) return fromToken.trim();
        if (email != null && !email.isBlank()) return email.split("@")[0];
        if (phone != null && !phone.isBlank()) return phone;
        return "User";
    }

    private String provider(String providerId) {
        if (providerId == null) return "FIREBASE";
        return switch (providerId) {
            case "google.com" -> "GOOGLE";
            case "phone" -> "PHONE";
            default -> providerId.toUpperCase();
        };
    }

    private String syntheticEmail(String phone, String uid) {
        if (phone != null && !phone.isBlank()) {
            return phone.replaceAll("[^0-9]", "") + "@phone.local";
        }
        return uid + "@firebase.local";
    }

    private String lower(String v) {
        return v == null ? null : v.trim().toLowerCase();
    }

    /* ====================== HELPERS ====================== */

    private String provider(String requested, String fallback) {
        if (requested == null || requested.isBlank()) return fallback;
        return requested.trim().toUpperCase();
    }

    private String safeProvider(User user) {
        String p = user.getAuthProvider();
        if (p == null || p.isBlank()) return "another method";
        return p.toLowerCase();
    }

    private String safeName(String name, String fallback) {
        if (name != null && !name.isBlank()) return name.trim();
        return fallback;
    }
}
