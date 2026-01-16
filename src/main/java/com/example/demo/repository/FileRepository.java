package com.example.demo.repository;

import com.example.demo.model.Folder;
import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<Folder, Long> {

    /**
     * Find all files belonging to a specific user.
     */
    List<Folder> findByUserOrderByUploadedAtDesc(User user);

    /**
     * Find a file by ID that belongs to a specific user.
     */
    Optional<Folder> findByIdAndUser(Long id, User user);

    /**
     * Count files for a specific user.
     */
    long countByUser(User user);

    /**
     * Calculate total size of files for a user.
     */
    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM Folder f WHERE f.user = :user")
    long sumFileSizeByUser(@Param("user") User user);

    /**
     * Find all files ordered by upload date (for admin).
     */
    List<Folder> findAllByOrderByUploadedAtDesc();

    /**
     * Calculate total size of all files (for admin).
     */
    @Query("SELECT COALESCE(SUM(f.fileSize), 0) FROM Folder f")
    long sumAllFileSize();

    /**
     * Delete all files belonging to a user (for admin when deleting user).
     */
    void deleteByUser(User user);

    Optional<Folder> findByFileName(String fileName);

    Optional<Folder> findByStoredFileName(String storedFileName);

    boolean existsByFileName(String fileName);
}
