package com.example.demo.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * User entity for authentication and file ownership.
 * Each user has their own encryption key for file encryption.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true, nullable = false, length = 100)
    private String email;

    @Column(nullable = false)
    private String password; // BCrypt hashed

    /**
     * User role for authorization (USER or ADMIN).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    /**
     * Per-user encryption key (Base64 encoded, 32 bytes for AES-256).
     * Generated automatically when user registers.
     */
    @Column(name = "encryption_key", nullable = false, length = 64)
    private String encryptionKey;

    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    /**
     * Check if user is an admin.
     */
    public boolean isAdmin() {
        return Role.ADMIN.equals(this.role);
    }
}
