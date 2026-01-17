package com.example.demo.service;

import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service for user management.
 *
 * ZERO-KNOWLEDGE ENCRYPTION:
 * - Only stores encryption salt, NOT the key
 * - Encryption key is derived from password at login
 * - Admins cannot decrypt user files
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EncryptionService encryptionService;

    /**
     * Register a new user with auto-generated encryption salt.
     */
    @Transactional
    public User registerUser(String username, String email, String password) {
        return registerUser(username, email, password, Role.USER);
    }

    /**
     * Register a new user with a specific role.
     * The encryption key is derived from password + salt at login time.
     */
    @Transactional
    public User registerUser(String username, String email, String password, Role role) {
        // Validate unique username
        if (userRepository.existsByUsername(username)) {
            throw new IllegalArgumentException("Username already exists: " + username);
        }

        // Validate unique email
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered: " + email);
        }

        // Generate unique salt (NOT the key!) for this user
        // The encryption key will be derived from password + salt at login
        String encryptionSalt = encryptionService.generateSalt();

        User user = User.builder()
                .username(username)
                .email(email)
                .password(passwordEncoder.encode(password))
                .role(role)
                .encryptionSalt(encryptionSalt)
                .enabled(true)
                .createdAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);
        log.info("New user registered: {} (id={}, role={}) - Zero-knowledge encryption enabled",
                username, savedUser.getId(), role);

        return savedUser;
    }

    /**
     * Derive the user's encryption key from their password.
     * This key is computed at login and stored ONLY in the session.
     */
    public String deriveEncryptionKey(User user, String plainPassword) {
        return encryptionService.deriveKeyFromPassword(plainPassword, user.getEncryptionSalt());
    }

    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
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

    // ============ Admin Methods ============

    /**
     * Get all users (admin only).
     */
    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findAllByOrderByCreatedAtDesc();
    }

    /**
     * Toggle user enabled status (admin only).
     */
    @Transactional
    public void toggleUserEnabled(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setEnabled(!user.getEnabled());
        userRepository.save(user);
        log.info("User {} enabled status changed to: {}", user.getUsername(), user.getEnabled());
    }

    /**
     * Change user role (admin only).
     */
    @Transactional
    public void changeUserRole(Long userId, Role newRole) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        user.setRole(newRole);
        userRepository.save(user);
        log.info("User {} role changed to: {}", user.getUsername(), newRole);
    }

    /**
     * Delete user (admin only).
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        userRepository.delete(user);
        log.info("User deleted: {} (id={})", user.getUsername(), userId);
    }

    /**
     * Count users by role.
     */
    @Transactional(readOnly = true)
    public long countByRole(Role role) {
        return userRepository.countByRole(role);
    }

    /**
     * Get total user count.
     */
    @Transactional(readOnly = true)
    public long getTotalUserCount() {
        return userRepository.count();
    }
}
