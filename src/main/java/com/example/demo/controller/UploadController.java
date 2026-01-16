package com.example.demo.controller;

import com.example.demo.model.Folder;
import com.example.demo.service.FolderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

/**
 * Controller for handling large file uploads and downloads.
 * Supports files up to 10GB with streaming.
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class UploadController {

    private final FolderService folderService;

    @GetMapping("/")
    public String index(Model model) {
        List<Folder> files = folderService.getAllFiles();
        FolderService.StorageStats stats = folderService.getStorageStats();

        model.addAttribute("files", files);
        model.addAttribute("totalFiles", stats.totalFiles());
        model.addAttribute("totalSize", folderService.formatFileSize(stats.totalSize()));
        model.addAttribute("availableSpace", folderService.formatFileSize(stats.availableSpace()));
        model.addAttribute("encryptionEnabled", stats.encryptionEnabled());

        return "upload";
    }

    @PostMapping("/upload")
    public String uploadFile(@RequestParam("file") MultipartFile file,
                             RedirectAttributes redirectAttributes) {
        if (file.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Please select a file to upload");
            return "redirect:/";
        }

        try {
            long startTime = System.currentTimeMillis();
            Folder folder = folderService.storeFile(file);
            long duration = System.currentTimeMillis() - startTime;

            String sizeFormatted = folderService.formatFileSize(folder.getFileSize());
            double speedMBps = folder.getFileSize() / (1024.0 * 1024.0) / (duration / 1000.0);

            log.info("File uploaded: {} ({}) in {}ms ({} MB/s)",
                    folder.getFileName(), sizeFormatted, duration, String.format("%.2f", speedMBps));

            redirectAttributes.addFlashAttribute("success",
                    String.format("File uploaded successfully: %s (%s) in %.1f seconds",
                            folder.getFileName(), sizeFormatted, duration / 1000.0));
        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Upload failed: " + e.getMessage());
        }

        return "redirect:/";
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) {
        Folder folder = folderService.getFileById(id);
        Resource resource = folderService.loadFileAsResource(id);

        String contentType = folder.getContentType();
        if (contentType == null || contentType.isEmpty()) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(folder.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + folder.getFileName() + "\"")
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .body(resource);
    }

    @PostMapping("/delete/{id}")
    public String deleteFile(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Folder folder = folderService.getFileById(id);
            String fileName = folder.getFileName();
            folderService.deleteFile(id);
            redirectAttributes.addFlashAttribute("success", "File deleted: " + fileName);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Delete failed: " + e.getMessage());
        }
        return "redirect:/";
    }

    @GetMapping("/api/stats")
    @ResponseBody
    public Map<String, Object> getStats() {
        FolderService.StorageStats stats = folderService.getStorageStats();
        return Map.of(
                "totalFiles", stats.totalFiles(),
                "totalSize", stats.totalSize(),
                "totalSizeFormatted", folderService.formatFileSize(stats.totalSize()),
                "availableSpace", stats.availableSpace(),
                "availableSpaceFormatted", folderService.formatFileSize(stats.availableSpace())
        );
    }
}
