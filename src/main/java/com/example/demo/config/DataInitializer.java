package com.example.demo.config;

import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Initializes the application with a default admin user if none exists.
 *
 * ZERO-KNOWLEDGE ENCRYPTION:
 * - Only stores encryption salt, NOT the key
 * - Admin's encryption key is derived from password at login
 * - Even the default admin cannot decrypt other users' files
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionService encryptionService;

    @org.springframework.beans.factory.annotation.Value("${admin.default.password:admin123}")
    private String defaultAdminPassword;

    @Override
    public void run(String... args) {
        // Create default admin if no admin exists
        if (userRepository.countByRole(Role.ADMIN) == 0) {
            log.info("No admin user found. Creating default admin...");

            User admin = User.builder()
                    .username("admin")
                    .email("admin@fileupload.local")
                    .password(passwordEncoder.encode(defaultAdminPassword))
                    .role(Role.ADMIN)
                    .encryptionSalt(encryptionService.generateSalt()) // Only salt, NOT the key!
                    .enabled(true)
                    .createdAt(LocalDateTime.now())
                    .build();

            userRepository.save(admin);
            log.info("=========================================");
            log.info("DEFAULT ADMIN USER CREATED:");
            log.info("  Username: admin");
            log.info("  Password: {}", defaultAdminPassword);
            log.info("  ⚠️  CHANGE THIS PASSWORD IMMEDIATELY!");
            log.info("  🔒 Zero-knowledge encryption enabled");
            log.info("=========================================");
        }
    }
}
