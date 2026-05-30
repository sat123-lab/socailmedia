package com.example.demo.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Typed view of {@code app.security.*} properties — all security knobs in one place.
 *
 * <p>In production, override these via environment variables, never commit real keys.</p>
 */
@Configuration
@ConfigurationProperties(prefix = "app.security")
@Data
public class SecurityProperties {

    private String encryptionKeyBase64;
    private Jwt jwt = new Jwt();
    private int roomTokenTtlSeconds = 120;
    private BruteForce bruteForce = new BruteForce();
    private RateLimit rateLimit = new RateLimit();
    private Cors cors = new Cors();
    private Audit audit = new Audit();

    @Data
    public static class Jwt {
        private String accessSecretBase64;
        private String refreshSecretBase64;
        private long accessTtlSeconds = 900;          // 15 minutes
        private long refreshTtlSeconds = 30L * 86400; // 30 days
    }

    @Data
    public static class BruteForce {
        private int maxAttempts = 5;
        private int lockoutMinutes = 15;
    }

    @Data
    public static class RateLimit {
        private int authRpm = 12;
        private int apiRpm = 240;
        private int uploadRpm = 30;
    }

    @Data
    public static class Cors {
        private String allowedOrigins = "*";
    }

    @Data
    public static class Audit {
        private int retentionDays = 180;
    }
}
