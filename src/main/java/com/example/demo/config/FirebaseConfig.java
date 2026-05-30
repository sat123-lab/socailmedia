package com.example.demo.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Initialises the Firebase Admin SDK at startup so we can verify
 * ID tokens issued by Firebase Auth (Google sign-in + Phone OTP).
 *
 * Configure the location of the service-account JSON via
 * {@code firebase.credentials-path} or the standard
 * {@code GOOGLE_APPLICATION_CREDENTIALS} env variable.
 */
@Configuration
public class FirebaseConfig {

    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    @Value("${firebase.credentials-path:}")
    private String credentialsPath;

    @Value("${firebase.enabled:true}")
    private boolean enabled;

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.warn("[Firebase] disabled via firebase.enabled=false");
            return;
        }
        if (!FirebaseApp.getApps().isEmpty()) {
            return;
        }

        try {
            GoogleCredentials credentials = loadCredentials();
            if (credentials == null) {
                log.warn("[Firebase] No service-account credentials found. " +
                        "Google + Phone auth will be disabled until a key file is provided.");
                return;
            }

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();
            FirebaseApp.initializeApp(options);
            log.info("[Firebase] initialised");
        } catch (Exception e) {
            log.warn("[Firebase] init failed: {}", e.getMessage());
        }
    }

    private GoogleCredentials loadCredentials() throws Exception {
        if (credentialsPath != null && !credentialsPath.isBlank()) {
            Path path = Paths.get(credentialsPath);
            if (Files.exists(path)) {
                try (InputStream in = new FileInputStream(path.toFile())) {
                    return GoogleCredentials.fromStream(in);
                }
            }
            ClassPathResource cp = new ClassPathResource(
                    credentialsPath.replace("src/main/resources/", "")
            );
            if (cp.exists()) {
                try (InputStream in = cp.getInputStream()) {
                    return GoogleCredentials.fromStream(in);
                }
            }
        }

        String envPath = System.getenv("GOOGLE_APPLICATION_CREDENTIALS");
        if (envPath != null && !envPath.isBlank() && Files.exists(Paths.get(envPath))) {
            try (InputStream in = new FileInputStream(envPath)) {
                return GoogleCredentials.fromStream(in);
            }
        }

        try {
            return GoogleCredentials.getApplicationDefault();
        } catch (Exception ignored) {
            return null;
        }
    }
}
