package com.example.demo.service;

import com.example.demo.exception.FileNotFoundException;
import com.example.demo.exception.FileStorageException;
import com.example.demo.model.Folder;
import com.example.demo.repository.FileRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;

/**
 * Service for handling large file uploads using streaming.
 * Files are streamed directly to disk without loading into memory.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FolderService {

    private static final int BUFFER_SIZE = 8192; // 8KB buffer for streaming

    @Value("${file.upload-dir}")
    private String uploadDir;

    private Path uploadPath;

    private final FileRepository fileRepository;

    @PostConstruct
    public void init() {
        uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
            log.info("Upload directory initialized at: {}", uploadPath);
        } catch (IOException e) {
            throw new FileStorageException("Could not create upload directory", e);
        }
    }

    /**
     * Store a file using streaming - suitable for large files (GB+).
     * The file is streamed directly to disk without loading into memory.
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

        String storedFileName = UUID.randomUUID() + "_" + originalFileName;
        Path targetLocation = uploadPath.resolve(storedFileName);

        try {
            // Stream file directly to disk with progress logging
            long fileSize = streamToFile(file.getInputStream(), targetLocation, file.getSize());

            log.info("File stored successfully: {} -> {} ({} bytes)",
                    originalFileName, storedFileName, fileSize);

            Folder folder = Folder.builder()
                    .fileName(originalFileName)
                    .storedFileName(storedFileName)
                    .contentType(file.getContentType())
                    .fileSize(fileSize)
                    .build();

            return fileRepository.save(folder);
        } catch (IOException e) {
            // Clean up partial file on failure
            try {
                Files.deleteIfExists(targetLocation);
            } catch (IOException ignored) {}
            throw new FileStorageException("Could not store file: " + originalFileName, e);
        }
    }

    /**
     * Stream input to file with buffered writing - memory efficient for large files.
     */
    private long streamToFile(InputStream inputStream, Path targetPath, long expectedSize) throws IOException {
        long totalBytes = 0;
        long lastLoggedPercent = 0;

        try (OutputStream outputStream = Files.newOutputStream(targetPath,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;

                // Log progress every 10% for large files
                if (expectedSize > 0) {
                    long percent = (totalBytes * 100) / expectedSize;
                    if (percent >= lastLoggedPercent + 10) {
                        log.debug("Upload progress: {}% ({} / {} bytes)",
                                percent, totalBytes, expectedSize);
                        lastLoggedPercent = percent;
                    }
                }
            }
            outputStream.flush();
        }

        return totalBytes;
    }

    /**
     * Load file as a Resource for streaming download.
     */
    @Transactional(readOnly = true)
    public Resource loadFileAsResource(Long id) {
        Folder folder = fileRepository.findById(id)
                .orElseThrow(() -> new FileNotFoundException("File not found with id: " + id));

        try {
            Path filePath = uploadPath.resolve(folder.getStoredFileName()).normalize();
            Resource resource = new UrlResource(filePath.toUri());

            if (resource.exists() && resource.isReadable()) {
                return resource;
            } else {
                throw new FileNotFoundException("File not found: " + folder.getFileName());
            }
        } catch (MalformedURLException e) {
            throw new FileNotFoundException("File not found: " + folder.getFileName(), e);
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

            return new StorageStats(totalFiles, totalSize, availableSpace);
        } catch (IOException e) {
            log.error("Error getting storage stats", e);
            return new StorageStats(0, 0, 0);
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

    public record StorageStats(long totalFiles, long totalSize, long availableSpace) {}
}
