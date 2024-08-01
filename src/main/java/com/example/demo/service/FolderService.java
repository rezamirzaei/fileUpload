package com.example.demo.service;

import com.example.demo.dao.FolderDAO;
import com.example.demo.model.Folder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Blob;
import java.util.List;

/**
 * Created by Reza-PC on 7/24/2017.
 */
@Service
public class FolderService {
    @Autowired
    FolderDAO folderDAO;

    @Transactional
    public void Create(Blob blob,String name){
        Folder folder = new Folder(name,blob);
        folderDAO.create(folder);
    }
    @Transactional
    public void Update(Blob blob,String name){
        Folder folder = loadByName(name);
        folder.setFile(blob);
        folderDAO.update(folder);
    }
    @Transactional
    public Folder loadByName(String name){
        return folderDAO.LoadByName(name);
    }
    @Transactional
    public List loadAllName(){
        return folderDAO.loadAllName();
    }

}
