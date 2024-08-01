package com.example.demo.model;



import org.springframework.web.multipart.MultipartFile;

import javax.persistence.*;
import java.sql.Blob;
/**
 * Created by Reza-PC on 7/24/2017.
 */
@Entity
public class Folder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;
    @Column(unique = true)
    String name;
    @Column
    Blob file;

    public Folder(){

    }

    public Folder(String name, Blob file) {
        this.name = name;
        this.file = file;
    }

    public Blob getFile() {
        return file;
    }

    public void setFile(Blob file) {
        this.file = file;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
