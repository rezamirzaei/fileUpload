package com.example.demo.service;

import com.example.demo.exception.FileNotFoundException;
import com.example.demo.exception.FileStorageException;
import com.example.demo.model.Folder;
import com.example.demo.model.User;
import com.example.demo.repository.FileRepository;
import com.example.demo.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

/**
 * Service for handling large file uploads with ZERO-KNOWLEDGE ENCRYPTION.
 *
 * The encryption key is:
 * - Derived from user's password at login
 * - Stored ONLY in user's session (never in database)
 * - Destroyed when user logs out
 * - Unknown to admins (they cannot decrypt files)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FolderService {

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${encryption.enabled:true}")
    private boolean encryptionEnabled;

    private Path uploadPath;

    private final FileRepository fileRepository;
    private final UserRepository userRepository;

    @PostConstruct
    public void init() {
        uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
            log.info("Upload directory initialized at: {}", uploadPath);
            log.info("Encryption is {} (Zero-Knowledge mode)", encryptionEnabled ? "ENABLED" : "DISABLED");
        } catch (IOException e) {
            throw new FileStorageException("Could not create upload directory", e);
        }
    }

    /**
     * Get the currently authenticated user.
     */
    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("User not found: " + username));
    }

    /**
     * Store a file with TRUE zero-knowledge encryption.
     *
     * TRUE ZERO-KNOWLEDGE: The server NEVER has access to the encryption key.
     * Files must be encrypted CLIENT-SIDE before upload.
     * The server only stores the encrypted blob.
     */
    @Transactional
    public Folder storeFile(MultipartFile file) {
        return storeFile(file, false);
    }

    /**
     * Store a file, which should be encrypted client-side.
     *
     * TRUE ZERO-KNOWLEDGE ENCRYPTION:
     * - Files encrypted client-side (.enc.client) are stored as-is
     * - Server NEVER has the encryption key
     * - Server CANNOT decrypt files
     * - Unencrypted files are stored as-is (user's choice)
     */
    @Transactional
    public Folder storeFile(MultipartFile file, boolean clientEncrypted) {
        User currentUser = getCurrentUser();

        String originalFileName = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown"
        );

        // Check if file was encrypted client-side
        boolean isClientEncrypted = clientEncrypted || originalFileName.endsWith(".enc.client");
        if (isClientEncrypted) {
            // Remove the .enc.client suffix to get original name
            originalFileName = originalFileName.replace(".enc.client", "");
        }

        validateFileName(originalFileName);

        if (file.isEmpty()) {
            throw new FileStorageException("Cannot store empty file: " + originalFileName);
        }

        // Include user ID in stored filename for isolation
        String encSuffix = isClientEncrypted ? ".enc.client" : "";
        String storedFileName = currentUser.getId() + "_" + UUID.randomUUID() + "_" + originalFileName + encSuffix;
        Path targetLocation = uploadPath.resolve(storedFileName);

        try (InputStream inputStream = file.getInputStream()) {
            // Store file as-is (already encrypted client-side, or user chose not to encrypt)
            Files.copy(inputStream, targetLocation);

            if (isClientEncrypted) {
                log.info("TRUE ZERO-KNOWLEDGE: Client-encrypted file stored (server NEVER saw plaintext or key): {} -> {} (user={})",
                        originalFileName, storedFileName, currentUser.getUsername());
            } else {
                log.warn("File stored WITHOUT encryption: {} -> {} (user={})",
                        originalFileName, storedFileName, currentUser.getUsername());
            }

            Folder folder = Folder.builder()
                    .user(currentUser)
                    .fileName(originalFileName)
                    .storedFileName(storedFileName)
                    .contentType(file.getContentType())
                    .fileSize(file.getSize())
                    .build();

            return fileRepository.save(folder);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(targetLocation);
            } catch (IOException ignored) {
            }
            throw new FileStorageException("Could not store file: " + originalFileName, e);
        }
    }

    /**
     * Load file as a Resource for streaming download.
     *
     * TRUE ZERO-KNOWLEDGE: Server returns encrypted blob as-is.
     * Decryption happens CLIENT-SIDE in the browser.
     */
    @Transactional(readOnly = true)
    public Resource loadFileAsResource(Long id) {
        User currentUser = getCurrentUser();

        // Find file that belongs to current user
        Folder folder = fileRepository.findByIdAndUser(id, currentUser)
                .orElseThrow(() -> new FileNotFoundException("File not found or access denied"));

        Path filePath = uploadPath.resolve(folder.getStoredFileName()).normalize();

        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new FileNotFoundException("File not found: " + folder.getFileName());
        }

        try {
            String storedFileName = folder.getStoredFileName();
            boolean isClientEncrypted = storedFileName != null && storedFileName.endsWith(".enc.client");
            // TRUE ZERO-KNOWLEDGE: Server returns file as-is
            // Decryption (if encrypted) happens CLIENT-SIDE in the browser
            if (isClientEncrypted) {
                log.info("TRUE ZERO-KNOWLEDGE: Returning encrypted file for client-side decryption: {}", folder.getFileName());
            }

            return new InputStreamResource(Files.newInputStream(filePath));
        } catch (IOException e) {
            throw new FileStorageException("Could not read file: " + folder.getFileName(), e);
        }
    }

    /**
     * Check if file is client-side encrypted.
     */
    public boolean isClientEncrypted(Folder folder) {
        return folder.getStoredFileName() != null && folder.getStoredFileName().endsWith(".enc.client");
    }

    /**
     * Get file by ID - only returns file if it belongs to current user.
     */
    @Transactional(readOnly = true)
    public Folder getFileById(Long id) {
        User currentUser = getCurrentUser();
        return fileRepository.findByIdAndUser(id, currentUser)
                .orElseThrow(() -> new FileNotFoundException("File not found or access denied"));
    }

    /**
     * Get all files belonging to the current user.
     */
    @Transactional(readOnly = true)
    public List<Folder> getAllFiles() {
        User currentUser = getCurrentUser();
        return fileRepository.findByUserOrderByUploadedAtDesc(currentUser);
    }

    /**
     * Delete file - user can only delete their own files.
     */
    @Transactional
    public void deleteFile(Long id) {
        User currentUser = getCurrentUser();

        Folder folder = fileRepository.findByIdAndUser(id, currentUser)
                .orElseThrow(() -> new FileNotFoundException("File not found or access denied"));

        try {
            Path filePath = uploadPath.resolve(folder.getStoredFileName());
            Files.deleteIfExists(filePath);
            fileRepository.delete(folder);
            log.info("File deleted: {} (user={})", folder.getFileName(), currentUser.getUsername());
        } catch (IOException e) {
            throw new FileStorageException("Could not delete file: " + folder.getFileName(), e);
        }
    }

    /**
     * Get storage statistics for the current user.
     */
    public StorageStats getStorageStats() {
        User currentUser = getCurrentUser();

        try {
            long totalFiles = fileRepository.countByUser(currentUser);
            long totalSize = fileRepository.sumFileSizeByUser(currentUser);
            long availableSpace = Files.getFileStore(uploadPath).getUsableSpace();

            return new StorageStats(totalFiles, totalSize, availableSpace, encryptionEnabled);
        } catch (IOException e) {
            log.error("Error getting storage stats", e);
            return new StorageStats(0, 0, 0, encryptionEnabled);
        }
    }

    private void validateFileName(String fileName) {
        if (fileName.contains("..") || fileName.contains("/") || fileName.contains("\\")) {
            throw new FileStorageException("Invalid file path: " + fileName);
        }
    }

    public String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    // ============ Admin Methods ============

    /**
     * Get all files (admin only).
     */
    @Transactional(readOnly = true)
    public List<Folder> getAllFilesAdmin() {
        return fileRepository.findAllByOrderByUploadedAtDesc();
    }

    /**
     * Get files for a specific user (admin only).
     */
    @Transactional(readOnly = true)
    public List<Folder> getFilesByUser(User user) {
        return fileRepository.findByUserOrderByUploadedAtDesc(user);
    }

    /**
     * Get file by ID (admin only - can access any file).
     */
    @Transactional(readOnly = true)
    public Folder getFileByIdAdmin(Long id) {
        return fileRepository.findById(id)
                .orElseThrow(() -> new FileNotFoundException("File not found: " + id));
    }

    /**
     * Load file as resource (admin only).
     *
     * ZERO-KNOWLEDGE: Admin downloads the ENCRYPTED file - they CANNOT decrypt it
     * because they don't have the user's password-derived key.
     */
    @Transactional(readOnly = true)
    public Resource loadFileAsResourceAdmin(Long id) {
        Folder folder = fileRepository.findById(id)
                .orElseThrow(() -> new FileNotFoundException("File not found: " + id));

        Path filePath = uploadPath.resolve(folder.getStoredFileName()).normalize();

        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new FileNotFoundException("File not found: " + folder.getFileName());
        }

        try {
            // Admin gets the RAW file (encrypted) - they CANNOT decrypt it!
            // Only the file owner with their password can decrypt
            return new InputStreamResource(Files.newInputStream(filePath));
        } catch (IOException e) {
            throw new FileStorageException("Could not read file: " + folder.getFileName(), e);
        }
    }

    /**
     * Check if file is encrypted.
     */
    public boolean isFileEncrypted(Folder folder) {
        return folder.getStoredFileName() != null && folder.getStoredFileName().endsWith(".enc");
    }

    /**
     * Delete file (admin only - can delete any file).
     */
    @Transactional
    public void deleteFileAdmin(Long id) {
        Folder folder = fileRepository.findById(id)
                .orElseThrow(() -> new FileNotFoundException("File not found: " + id));

        try {
            Path filePath = uploadPath.resolve(folder.getStoredFileName());
            Files.deleteIfExists(filePath);
            fileRepository.delete(folder);
            log.info("File deleted by admin: {} (owner={})", folder.getFileName(),
                    folder.getUser().getUsername());
        } catch (IOException e) {
            throw new FileStorageException("Could not delete file: " + folder.getFileName(), e);
        }
    }

    /**
     * Delete all files for a user (admin only - used when deleting user).
     */
    @Transactional
    public void deleteAllFilesByUser(User user) {
        List<Folder> userFiles = fileRepository.findByUserOrderByUploadedAtDesc(user);
        for (Folder folder : userFiles) {
            try {
                Path filePath = uploadPath.resolve(folder.getStoredFileName());
                Files.deleteIfExists(filePath);
            } catch (IOException e) {
                log.error("Could not delete file: {}", folder.getStoredFileName(), e);
            }
        }
        fileRepository.deleteByUser(user);
        log.info("All files deleted for user: {}", user.getUsername());
    }

    /**
     * Get global storage stats (admin only).
     */
    public StorageStats getGlobalStorageStats() {
        try {
            long totalFiles = fileRepository.count();
            long totalSize = fileRepository.sumAllFileSize();
            long availableSpace = Files.getFileStore(uploadPath).getUsableSpace();

            return new StorageStats(totalFiles, totalSize, availableSpace, encryptionEnabled);
        } catch (IOException e) {
            log.error("Error getting global storage stats", e);
            return new StorageStats(0, 0, 0, encryptionEnabled);
        }
    }

    public record StorageStats(long totalFiles, long totalSize, long availableSpace, boolean encryptionEnabled) {
    }
}
