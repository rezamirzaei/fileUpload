package com.example.demo.service;

import com.example.demo.exception.FileStorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Service for encrypting and decrypting files using AES-256-GCM.
 * Supports per-user encryption keys for multi-tenant isolation.
 */
@Service
@Slf4j
public class EncryptionService {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits recommended for GCM
    private static final int GCM_TAG_LENGTH = 128; // 128 bits authentication tag
    private static final int BUFFER_SIZE = 8192; // 8KB buffer for streaming

    /**
     * Create a SecretKey from a Base64-encoded key string.
     */
    public SecretKey createSecretKey(String base64Key) {
        try {
            byte[] decodedKey = Base64.getDecoder().decode(base64Key.trim());
            if (decodedKey.length != 32) {
                throw new IllegalArgumentException(
                        "Secret key must decode to exactly 32 bytes (256 bits) for AES-256-GCM."
                );
            }
            return new SecretKeySpec(decodedKey, ALGORITHM);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Invalid encryption key format.", e);
        }
    }

    /**
     * Generate a new random AES-256 key (for new user registration).
     */
    public String generateNewKey() {
        byte[] key = new byte[32]; // 256 bits
        new SecureRandom().nextBytes(key);
        return Base64.getEncoder().encodeToString(key);
    }

    /**
     * Encrypt an input stream and write to a file using the provided user key.
     */
    public long encryptToFile(InputStream inputStream, Path targetPath, long expectedSize, String userKey) {
        SecretKey secretKey = createSecretKey(userKey);

        try {
            // Generate random IV for each file
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec);

            long totalPlaintextBytes = 0;
            long lastLoggedPercent = 0;

            try (OutputStream fileOutputStream = Files.newOutputStream(targetPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

                // Write IV first (unencrypted, needed for decryption)
                fileOutputStream.write(iv);

                try (CipherOutputStream cipherOutputStream = new CipherOutputStream(fileOutputStream, cipher)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead;

                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        cipherOutputStream.write(buffer, 0, bytesRead);
                        totalPlaintextBytes += bytesRead;

                        // Log progress every 10%
                        if (expectedSize > 0) {
                            long percent = (totalPlaintextBytes * 100) / expectedSize;
                            if (percent >= lastLoggedPercent + 10) {
                                log.debug("Encryption progress: {}% ({} / {} bytes)",
                                        percent, totalPlaintextBytes, expectedSize);
                                lastLoggedPercent = percent;
                            }
                        }
                    }
                }
            }

            return totalPlaintextBytes;

        } catch (Exception e) {
            // Clean up partial file on failure
            try {
                Files.deleteIfExists(targetPath);
            } catch (Exception ignored) {
            }
            throw new FileStorageException("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * Create a decrypting input stream from an encrypted file using the provided user key.
     */
    public InputStream decryptFromFile(Path encryptedFilePath, String userKey) {
        SecretKey secretKey = createSecretKey(userKey);

        try {
            InputStream fileInputStream = Files.newInputStream(encryptedFilePath);

            // Read IV from beginning of file
            byte[] iv = new byte[GCM_IV_LENGTH];
            int bytesRead = fileInputStream.read(iv);
            if (bytesRead != GCM_IV_LENGTH) {
                fileInputStream.close();
                throw new FileStorageException("Invalid encrypted file: could not read IV");
            }

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec);

            return new CipherInputStream(fileInputStream, cipher);

        } catch (FileStorageException e) {
            throw e;
        } catch (Exception e) {
            throw new FileStorageException("Decryption failed: " + e.getMessage(), e);
        }
    }
}
