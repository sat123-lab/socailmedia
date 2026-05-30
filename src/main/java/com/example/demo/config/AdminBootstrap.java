package com.example.demo.config;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.email:admin@vibeu.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@123}")
    private String adminPassword;

    @Value("${app.admin.name:VIBE U Admin}")
    private String adminName;

    @Override
    public void run(ApplicationArguments args) {
        User admin = userRepository.findByEmail(adminEmail.trim().toLowerCase())
                .orElseGet(() -> {
                    User created = User.builder()
                            .name(adminName)
                            .email(adminEmail.trim().toLowerCase())
                            .phone("0000000000")
                            .password(passwordEncoder.encode(adminPassword))
                            .authProvider("local")
                            .admin(true)
                            .privateAccount(false)
                            .build();
                    User saved = userRepository.save(created);
                    System.out.println("AdminBootstrap: created admin " + adminEmail);
                    return saved;
                });

        admin.setAdmin(true);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        userRepository.save(admin);

        System.out.println("AdminBootstrap: admin login -> " + adminEmail + " / " + adminPassword);
    }
}
