package com.example.demo.config;

import com.example.demo.model.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.EncryptionService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Custom authentication handler that derives the user's encryption key
 * from their password at login time and stores it in the session.
 *
 * ZERO-KNOWLEDGE ENCRYPTION:
 * - The encryption key is derived from: password + user's salt
 * - The key is stored ONLY in the session (never in database)
 * - When session ends, the key is gone
 * - Admins cannot access the key or decrypt files
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EncryptionKeyAuthenticationHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserRepository userRepository;
    private final EncryptionService encryptionService;

    /**
     * Session attribute name for storing the derived encryption key.
     */
    public static final String ENCRYPTION_KEY_SESSION_ATTRIBUTE = "USER_ENCRYPTION_KEY";

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        String username = authentication.getName();
        String password = request.getParameter("password"); // Get plaintext password from login form

        if (password != null) {
            User user = userRepository.findByUsername(username).orElse(null);

            if (user != null) {
                // Derive encryption key from password + salt
                String encryptionKey = encryptionService.deriveKeyFromPassword(
                        password, user.getEncryptionSalt());

                // Store the derived key in session ONLY
                HttpSession session = request.getSession();
                session.setAttribute(ENCRYPTION_KEY_SESSION_ATTRIBUTE, encryptionKey);

                // Update last login time
                user.setLastLogin(LocalDateTime.now());
                userRepository.save(user);

                log.info("User {} logged in - encryption key derived and stored in session", username);
            }
        }

        // Clear password from memory (best effort)
        // Note: The actual parameter is managed by the servlet container

        super.onAuthenticationSuccess(request, response, authentication);
    }
}
