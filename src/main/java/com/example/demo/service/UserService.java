package com.example.demo.service;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Register a new user with auto-generated encryption key.
     */
    @Transactional
    public User registerUser(String username, String email, String password) {
        // Validate unique username
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }

        // Validate unique email
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }

        // Generate unique encryption key for this user (32 bytes for AES-256)
        String encryptionKey = generateUserEncryptionKey();

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .encryptionKey(encryptionKey)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);
        log.info("New user registered: {} (id={})", username, savedUser.getId());

        return savedUser;
    }

    /**
     * Generate a unique AES-256 encryption key for a user.
     */
    private String generateUserEncryptionKey() {
        byte[] key = new byte[32]; // 256 bits
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional
    public void updateLastLogin(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);
        });
    }

    @Transactional(readOnly = true)
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }

    @Transactional(readOnly = true)
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
}
