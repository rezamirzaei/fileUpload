package com.example.demo.repository;

import com.example.demo.model.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface FileRepository extends JpaRepository<Folder, Long> {

    Optional<Folder> findByFileName(String fileName);

    Optional<Folder> findByStoredFileName(String storedFileName);

    boolean existsByFileName(String fileName);
}
