package com.onlinetoolhub.OnlineToolHub.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;

import com.onlinetoolhub.OnlineToolHub.service.ImageService;

/**
 * Controller for handling image-related operations, such as processing
 * and packaging images with generated captions.
 */
@RestController
@RequestMapping("/api/images")
@CrossOrigin("*")
public class ImageController {

    @Autowired
    private ImageService imageService;

    /**
     * Processes a list of images by generating captions and packaging them into a ZIP file.
     *
     * @param images a list of images uploaded by the client
     * @return a ZIP file (as bytes) containing the processed images
     */
    @PostMapping("/process")
    public ResponseEntity<?> processImages(@RequestParam("images") List<MultipartFile> images) {
        try {
            // Generate a caption for each image
            List<String> processedImageNames = images.stream().map(image -> {
                try {
                    return imageService.getCaption(image);
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }).collect(Collectors.toList());

            // Create a ZIP file of the processed images (with updated captions as file names)
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
}
