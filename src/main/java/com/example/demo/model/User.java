package com.example.demo.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * User entity for authentication and file ownership.
 *
 * ZERO-KNOWLEDGE ENCRYPTION:
 * - Only the encryption salt is stored, NOT the encryption key
 * - The encryption key is derived from password + salt at login
 * - The derived key exists only in the user's session
 * - Admins CANNOT decrypt user files
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
    private String password; // BCrypt hashed (for authentication only)

    /**
     * User role for authorization (USER or ADMIN).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Role role = Role.USER;

    /**
     * Salt for deriving the user's encryption key.
     * The actual encryption key is derived from: password + salt
     * and is NEVER stored in the database.
     *
     * This enables zero-knowledge encryption where:
     * - Only the user can decrypt their files
     * - Admins cannot access file contents
     * - If user forgets password, files are unrecoverable
     */
    @Column(name = "encryption_salt", nullable = false, length = 64)
    private String encryptionSalt;

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
