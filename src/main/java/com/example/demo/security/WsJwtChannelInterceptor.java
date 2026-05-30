package com.example.demo.security;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Validates the JWT on every STOMP CONNECT and pins the resulting principal on
 * the session. All subsequent SUBSCRIBE / SEND frames inherit that principal.
 *
 * <p>Without this filter any client could connect to {@code /ws} and listen to
 * other users' topics — privacy disaster.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WsJwtChannelInterceptor implements ChannelInterceptor {

    private final TokenManager tokenManager;
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String auth = firstHeader(accessor, "Authorization");
            String token = (auth != null && auth.startsWith("Bearer ")) ? auth.substring(7) : null;
            if (token == null) {
                log.warn("WS CONNECT rejected — no Authorization header.");
                throw new SecurityException("Missing token");
            }

            Optional<Claims> parsed = tokenManager.parseAccessToken(token);
            String email;
            String role = "USER";
            if (parsed.isPresent()) {
                email = parsed.get().getSubject();
                role = String.valueOf(parsed.get().get(TokenManager.CLAIM_ROLE));
            } else {
                // legacy HS256 tokens still allowed during transition
                try {
                    email = jwtUtil.extractEmail(token);
                    if (!jwtUtil.validateToken(token, email)) {
                        throw new SecurityException("Bad JWT");
                    }
                } catch (Exception e) {
                    throw new SecurityException("Invalid token");
                }
            }

            User user = userRepository.findByEmail(email).orElse(null);
            if (user == null) throw new SecurityException("User not found");

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            user.getId().toString(),
                            null,
                            "ADMIN".equals(role)
                                    ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"),
                                              new SimpleGrantedAuthority("ROLE_USER"))
                                    : List.of(new SimpleGrantedAuthority("ROLE_USER")));
            accessor.setUser(authToken);
            accessor.getSessionAttributes().put("userId", user.getId());
            accessor.getSessionAttributes().put("email", email);
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String dest = accessor.getDestination();
            Long uid = (Long) accessor.getSessionAttributes().get("userId");
            // Block subscriptions to other users' private user-topics.
            if (dest != null && dest.startsWith("/topic/user/")) {
                String suffix = dest.substring("/topic/user/".length());
                try {
                    long requested = Long.parseLong(suffix);
                    if (uid == null || requested != uid) {
                        log.warn("WS SUBSCRIBE rejected — uid {} tried {}", uid, dest);
                        throw new SecurityException("Cross-user subscribe denied");
                    }
                } catch (NumberFormatException ignored) {
                    throw new SecurityException("Invalid topic");
                }
            }
            // Block /topic/admin/** for non-admins. The Private Chats Monitor
            // subscribes to /topic/admin/private-chats — must be admin only.
            if (dest != null && dest.startsWith("/topic/admin")) {
                Object principal = accessor.getUser();
                boolean isAdmin = principal instanceof
                        org.springframework.security.core.Authentication auth &&
                        auth.getAuthorities().stream().anyMatch(
                                g -> "ROLE_ADMIN".equals(g.getAuthority()));
                if (!isAdmin) {
                    log.warn("WS SUBSCRIBE rejected — non-admin uid {} tried {}", uid, dest);
                    throw new SecurityException("Admin subscription denied");
                }
            }
        }

        return message;
    }

    private static String firstHeader(StompHeaderAccessor accessor, String name) {
        List<String> v = accessor.getNativeHeader(name);
        return (v == null || v.isEmpty()) ? null : v.get(0);
    }
}
