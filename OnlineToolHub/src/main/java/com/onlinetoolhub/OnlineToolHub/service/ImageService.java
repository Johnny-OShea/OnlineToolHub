package com.onlinetoolhub.OnlineToolHub.service;

import java.io.IOException;
import java.util.List;

import org.springframework.web.multipart.MultipartFile;

/**
 * Service interface for image processing operations, including caption generation
 * and ZIP file creation.
 */
public interface ImageService {

    /**
     * Generates a caption for the given image using AI-based image analysis.
     *
     * @param image the image to analyze
     * @return a descriptive caption of the image
     * @throws IOException if an I/O error occurs during image processing
     */
    String getCaption(MultipartFile image) throws IOException;

    /**
     * Creates a ZIP file containing processed images.
     *
     * @param images        a list of image captions/names
     * @param originalFiles the original image files
     * @return a byte array representing the contents of the created ZIP file
     * @throws IOException if an I/O error occurs during ZIP creation
     */
    byte[] createZip(List<String> images, List<MultipartFile> originalFiles) throws IOException;
}
