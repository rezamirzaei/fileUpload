package com.example.demo.Controller;

import com.example.demo.service.FolderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.List;

/**
 * Created by Reza-PC on 7/24/2017.
 */
@Controller
public class UploadController{
    @Autowired
    FolderService folderService;
    @RequestMapping(value = "upload",method = RequestMethod.GET)
    public String uploadPage(Model model){
        List list = folderService.loadAllName();
        model.addAttribute("names",list);
        return "upload";
    }
    @RequestMapping(value = "upload",method = RequestMethod.POST)
    public String upload(@RequestParam("file")MultipartFile multipartFile, @RequestParam("name")String name, Model model) throws IOException, SQLException {
        byte [] byteArr=multipartFile.getBytes();
        java.sql.Blob blob = new javax.sql.rowset.serial.SerialBlob(byteArr);
        folderService.Create(blob,name);
        List list = folderService.loadAllName();
        model.addAttribute("names",list);
        return "upload";
    }

}
