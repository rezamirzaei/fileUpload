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
 * Service for handling large file uploads using streaming with per-user encryption.
 * Each user has their own encryption key - files are isolated per user.
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
    private final EncryptionService encryptionService;

    @PostConstruct
    public void init() {
        uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
            log.info("Upload directory initialized at: {}", uploadPath);
            log.info("Encryption is {}", encryptionEnabled ? "ENABLED (per-user keys)" : "DISABLED");
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
     * Store a file with per-user encryption.
     * The file is encrypted using the user's unique key and streamed directly to disk.
     */
    @Transactional
    public Folder storeFile(MultipartFile file) {
        User currentUser = getCurrentUser();

        String originalFileName = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown"
        );

        validateFileName(originalFileName);

        if (file.isEmpty()) {
            throw new FileStorageException("Cannot store empty file: " + originalFileName);
        }

        // Include user ID in stored filename for isolation
        String storedFileName = currentUser.getId() + "_" + UUID.randomUUID() + "_" + originalFileName
                + (encryptionEnabled ? ".enc" : "");
        Path targetLocation = uploadPath.resolve(storedFileName);

        try (InputStream inputStream = file.getInputStream()) {
            if (encryptionEnabled) {
                // Encrypt with user's personal key
                encryptionService.encryptToFile(inputStream, targetLocation, file.getSize(),
                        currentUser.getEncryptionKey());
                log.info("File encrypted with user key and stored: {} -> {} (user={})",
                        originalFileName, storedFileName, currentUser.getUsername());
            } else {
                Files.copy(inputStream, targetLocation);
                log.info("File stored (unencrypted): {} -> {} (user={})",
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
     * Load file as a Resource for streaming download (with per-user decryption).
     * User can only download their own files.
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
            boolean isEncryptedFile = folder.getStoredFileName() != null
                    && folder.getStoredFileName().endsWith(".enc");
            if (isEncryptedFile) {
                // Decrypt with user's personal key
                InputStream decryptedStream = encryptionService.decryptFromFile(filePath,
                        currentUser.getEncryptionKey());
                return new InputStreamResource(decryptedStream);
            }

            return new InputStreamResource(Files.newInputStream(filePath));
        } catch (IOException e) {
            throw new FileStorageException("Could not read file: " + folder.getFileName(), e);
        }
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
     * Load file as resource (admin only - can download any file using owner's key).
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
            boolean isEncryptedFile = folder.getStoredFileName() != null
                    && folder.getStoredFileName().endsWith(".enc");
            if (isEncryptedFile) {
                // Decrypt with file owner's key
                InputStream decryptedStream = encryptionService.decryptFromFile(filePath,
                        folder.getUser().getEncryptionKey());
                return new InputStreamResource(decryptedStream);
            }

            return new InputStreamResource(Files.newInputStream(filePath));
        } catch (IOException e) {
            throw new FileStorageException("Could not read file: " + folder.getFileName(), e);
        }
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
