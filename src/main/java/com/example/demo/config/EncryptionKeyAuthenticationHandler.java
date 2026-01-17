package com.example.demo.config;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Custom authentication handler for login success.
 *
 * TRUE ZERO-KNOWLEDGE ENCRYPTION:
 * - The server NEVER derives or stores the encryption key
 * - Key derivation happens ONLY in the browser (client-side JavaScript)
 * - The server only stores the salt (useless without the password)
 * - Even if the server is fully compromised, files remain encrypted
 *
 * The encryption key is derived CLIENT-SIDE using:
 *   PBKDF2(password, salt, 310000 iterations) → AES-256 key
 *
 * This key is stored in browser's localStorage and NEVER sent to the server.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EncryptionKeyAuthenticationHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserRepository userRepository;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        String username = authentication.getName();

        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            // Update last login time only - NO key derivation on server!
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);

            log.info("User {} logged in - encryption key will be derived CLIENT-SIDE only", username);
        }

        // Password is used ONLY for BCrypt authentication
        // It is NOT used to derive encryption key on server
        // The encryption key is derived in the browser using JavaScript

        super.onAuthenticationSuccess(request, response, authentication);
    }
}
