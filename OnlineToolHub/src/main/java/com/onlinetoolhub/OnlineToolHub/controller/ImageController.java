package com.onlinetoolhub.OnlineToolHub.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.onlinetoolhub.OnlineToolHub.service.ImageService;

import org.springframework.beans.factory.annotation.Autowired;


@RestController
@RequestMapping("/api/images")
@CrossOrigin("*")
public class ImageController {

    @Autowired
    private ImageService imageService;
    
    @PostMapping("/process")
    public ResponseEntity<?> processImages(@RequestParam("images") List<MultipartFile> images) {
    	System.out.println("Received " + images.size() + " files.");
        try {
            // Process each image
            List<String> processedImageNames = images.stream().map(image -> {
                try {
                	return imageService.getCaption(image);
                } catch (Exception e) {
                	System.out.println(image.getName() + "Failed to process");
                    e.printStackTrace();
                    return null;
                }
            }).collect(Collectors.toList());

            // Here, we create a Zip file based on the new names
            byte[] zipBytes = imageService.createZip(processedImageNames, images);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "processed_images.zip");

            return ResponseEntity.status(HttpStatus.OK)
                    .headers(headers)
                    .body(zipBytes);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body("Error processing images");
        }
    }
    
    @GetMapping("/api/hello")
    @CrossOrigin("*")
    public String helloWorld() {
        return "Hello World!";
    }
}
