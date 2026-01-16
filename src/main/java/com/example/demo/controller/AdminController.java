package com.example.demo.controller;

import com.example.demo.model.Folder;
import com.example.demo.model.Role;
import com.example.demo.model.User;
import com.example.demo.service.FolderService;
import com.example.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

/**
 * Admin controller for managing users and files.
 * All endpoints require ADMIN role.
 */
@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Slf4j
public class AdminController {

    private final UserService userService;
    private final FolderService folderService;

    /**
     * Admin dashboard - shows overview of users and files.
     */
    @GetMapping
    public String adminDashboard(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        List<User> users = userService.getAllUsers();
        FolderService.StorageStats stats = folderService.getGlobalStorageStats();

        model.addAttribute("users", users);
        model.addAttribute("totalUsers", userService.getTotalUserCount());
        model.addAttribute("totalAdmins", userService.countByRole(Role.ADMIN));
        model.addAttribute("totalFiles", stats.totalFiles());
        model.addAttribute("totalSize", folderService.formatFileSize(stats.totalSize()));
        model.addAttribute("availableSpace", folderService.formatFileSize(stats.availableSpace()));
        model.addAttribute("username", userDetails.getUsername());

        return "admin/dashboard";
    }

    /**
     * View all users.
     */
    @GetMapping("/users")
    public String viewUsers(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        List<User> users = userService.getAllUsers();
        model.addAttribute("users", users);
        model.addAttribute("username", userDetails.getUsername());
        return "admin/users";
    }

    /**
     * View files for a specific user.
     */
    @GetMapping("/users/{userId}/files")
    public String viewUserFiles(@PathVariable Long userId, Model model,
                                @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        List<Folder> files = folderService.getFilesByUser(user);

        model.addAttribute("targetUser", user);
        model.addAttribute("files", files);
        model.addAttribute("username", userDetails.getUsername());
        model.addAttribute("folderService", folderService);

        return "admin/user-files";
    }

    /**
     * Toggle user enabled/disabled status.
     */
    @PostMapping("/users/{userId}/toggle")
    public String toggleUserStatus(@PathVariable Long userId, RedirectAttributes redirectAttributes,
                                   @AuthenticationPrincipal UserDetails userDetails) {
        try {
            User user = userService.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            // Prevent admin from disabling themselves
            if (user.getUsername().equals(userDetails.getUsername())) {
                redirectAttributes.addFlashAttribute("error", "You cannot disable your own account");
                return "redirect:/admin/users";
            }

            userService.toggleUserEnabled(userId);
            redirectAttributes.addFlashAttribute("success",
                    "User " + user.getUsername() + " status changed");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to toggle user: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    /**
     * Change user role.
     */
    @PostMapping("/users/{userId}/role")
    public String changeUserRole(@PathVariable Long userId, @RequestParam("role") String role,
                                 RedirectAttributes redirectAttributes,
                                 @AuthenticationPrincipal UserDetails userDetails) {
        try {
            User user = userService.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            // Prevent admin from changing their own role
            if (user.getUsername().equals(userDetails.getUsername())) {
                redirectAttributes.addFlashAttribute("error", "You cannot change your own role");
                return "redirect:/admin/users";
            }

            Role newRole = Role.valueOf(role.toUpperCase());
            userService.changeUserRole(userId, newRole);
            redirectAttributes.addFlashAttribute("success",
                    "User " + user.getUsername() + " role changed to " + newRole);
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to change role: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    /**
     * Delete a user and all their files.
     */
    @PostMapping("/users/{userId}/delete")
    public String deleteUser(@PathVariable Long userId, RedirectAttributes redirectAttributes,
                             @AuthenticationPrincipal UserDetails userDetails) {
        try {
            User user = userService.findById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("User not found"));

            // Prevent admin from deleting themselves
            if (user.getUsername().equals(userDetails.getUsername())) {
                redirectAttributes.addFlashAttribute("error", "You cannot delete your own account");
                return "redirect:/admin/users";
            }

            String username = user.getUsername();

            // First delete all user's files
            folderService.deleteAllFilesByUser(user);

            // Then delete the user
            userService.deleteUser(userId);

            redirectAttributes.addFlashAttribute("success", "User " + username + " deleted");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete user: " + e.getMessage());
        }
        return "redirect:/admin/users";
    }

    /**
     * View all files in the system.
     */
    @GetMapping("/files")
    public String viewAllFiles(Model model, @AuthenticationPrincipal UserDetails userDetails) {
        List<Folder> files = folderService.getAllFilesAdmin();
        FolderService.StorageStats stats = folderService.getGlobalStorageStats();

        model.addAttribute("files", files);
        model.addAttribute("totalFiles", stats.totalFiles());
        model.addAttribute("totalSize", folderService.formatFileSize(stats.totalSize()));
        model.addAttribute("username", userDetails.getUsername());
        model.addAttribute("folderService", folderService);

        return "admin/files";
    }

    /**
     * Download any file (admin can access all files).
     */
    @GetMapping("/files/{id}/download")
    public ResponseEntity<Resource> downloadFile(@PathVariable Long id) {
        Folder folder = folderService.getFileByIdAdmin(id);
        Resource resource = folderService.loadFileAsResourceAdmin(id);

        String contentType = folder.getContentType();
        if (contentType == null || contentType.isEmpty()) {
            contentType = "application/octet-stream";
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .contentLength(folder.getFileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + folder.getFileName() + "\"")
                .body(resource);
    }

    /**
     * Delete any file (admin can delete all files).
     */
    @PostMapping("/files/{id}/delete")
    public String deleteFile(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Folder folder = folderService.getFileByIdAdmin(id);
            String fileName = folder.getFileName();
            String owner = folder.getUser().getUsername();

            folderService.deleteFileAdmin(id);

            redirectAttributes.addFlashAttribute("success",
                    "File '" + fileName + "' (owner: " + owner + ") deleted");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Failed to delete file: " + e.getMessage());
        }
        return "redirect:/admin/files";
    }
}
