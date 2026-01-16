package com.example.demo.service;

import com.example.demo.exception.FileNotFoundException;
import com.example.demo.exception.FileStorageException;
import com.example.demo.model.Folder;
import com.example.demo.repository.FileRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
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
 * Service for handling large file uploads using streaming with encryption.
 * When enabled, files are encrypted using AES-256-GCM before storage.
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
    private final EncryptionService encryptionService;

    @PostConstruct
    public void init() {
        uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
            log.info("Upload directory initialized at: {}", uploadPath);
            log.info("Encryption is {}", encryptionEnabled ? "ENABLED" : "DISABLED");
        } catch (IOException e) {
            throw new FileStorageException("Could not create upload directory", e);
        }
    }

    /**
     * Store a file with encryption - suitable for large files (GB+).
     * The file is encrypted and streamed directly to disk.
     */
    @Transactional
    public Folder storeFile(MultipartFile file) {
        String originalFileName = StringUtils.cleanPath(
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown"
        );

        validateFileName(originalFileName);

        if (file.isEmpty()) {
            throw new FileStorageException("Cannot store empty file: " + originalFileName);
        }

        String storedFileName = UUID.randomUUID() + "_" + originalFileName + (encryptionEnabled ? ".enc" : "");
        Path targetLocation = uploadPath.resolve(storedFileName);

        try (InputStream inputStream = file.getInputStream()) {
            if (encryptionEnabled) {
                encryptionService.encryptToFile(inputStream, targetLocation, file.getSize());
                log.info("File encrypted and stored: {} -> {}", originalFileName, storedFileName);
            } else {
                Files.copy(inputStream, targetLocation);
                log.info("File stored (unencrypted): {} -> {}", originalFileName, storedFileName);
            }

            Folder folder = Folder.builder()
                    .fileName(originalFileName)
                    .storedFileName(storedFileName)
                    .contentType(file.getContentType())
                    // Store original (plaintext) size for correct download Content-Length
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
     * Load file as a Resource for streaming download (with decryption if needed).
     */
    @Transactional(readOnly = true)
    public Resource loadFileAsResource(Long id) {
        Folder folder = fileRepository.findById(id)
                .orElseThrow(() -> new FileNotFoundException("File not found with id: " + id));

        Path filePath = uploadPath.resolve(folder.getStoredFileName()).normalize();

        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new FileNotFoundException("File not found: " + folder.getFileName());
        }

        try {
            boolean isEncryptedFile = folder.getStoredFileName() != null && folder.getStoredFileName().endsWith(".enc");
            if (isEncryptedFile) {
                if (!encryptionEnabled) {
                    throw new FileStorageException("Encrypted file present but encryption is disabled. Enable encryption to download.");
                }
                InputStream decryptedStream = encryptionService.decryptFromFile(filePath);
                return new InputStreamResource(decryptedStream);
            }

            return new InputStreamResource(Files.newInputStream(filePath));
        } catch (IOException e) {
            throw new FileStorageException("Could not read file: " + folder.getFileName(), e);
        }
    }

    @Transactional(readOnly = true)
    public Folder getFileById(Long id) {
        return fileRepository.findById(id)
                .orElseThrow(() -> new FileNotFoundException("File not found with id: " + id));
    }

    @Transactional(readOnly = true)
    public List<Folder> getAllFiles() {
        return fileRepository.findAll();
    }

    @Transactional
    public void deleteFile(Long id) {
        Folder folder = fileRepository.findById(id)
                .orElseThrow(() -> new FileNotFoundException("File not found with id: " + id));

        try {
            Path filePath = uploadPath.resolve(folder.getStoredFileName());
            Files.deleteIfExists(filePath);
            fileRepository.delete(folder);
            log.info("File deleted successfully: {}", folder.getFileName());
        } catch (IOException e) {
            throw new FileStorageException("Could not delete file: " + folder.getFileName(), e);
        }
    }

    /**
     * Get storage statistics.
     */
    public StorageStats getStorageStats() {
        try {
            long totalFiles = fileRepository.count();
            long totalSize = fileRepository.findAll().stream()
                    .mapToLong(f -> f.getFileSize() != null ? f.getFileSize() : 0)
                    .sum();
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

    public record StorageStats(long totalFiles, long totalSize, long availableSpace, boolean encryptionEnabled) {
    }
}
