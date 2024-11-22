package com.onlinetoolhub.OnlineToolHub.service;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

public interface ImageService {
	
	String getCaption(MultipartFile image) throws IOException;
    
    byte[] createZip(List<String> images, List<MultipartFile> originalFiles) throws IOException;
}
