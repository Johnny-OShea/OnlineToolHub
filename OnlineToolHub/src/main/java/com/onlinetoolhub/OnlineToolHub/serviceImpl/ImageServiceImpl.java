package com.onlinetoolhub.OnlineToolHub.serviceImpl;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.imageio.ImageIO;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.OpenAIClientBuilder;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatRequestMessage;
import com.azure.ai.openai.models.ChatRequestSystemMessage;
import com.azure.ai.openai.models.ChatRequestUserMessage;
import com.azure.ai.vision.imageanalysis.ImageAnalysisClient;
import com.azure.ai.vision.imageanalysis.ImageAnalysisClientBuilder;
import com.azure.ai.vision.imageanalysis.models.DenseCaption;
import com.azure.ai.vision.imageanalysis.models.DetectedTag;
import com.azure.ai.vision.imageanalysis.models.DetectedTextBlock;
import com.azure.ai.vision.imageanalysis.models.DetectedTextLine;
import com.azure.ai.vision.imageanalysis.models.ImageAnalysisOptions;
import com.azure.ai.vision.imageanalysis.models.ImageAnalysisResult;
import com.azure.ai.vision.imageanalysis.models.VisualFeatures;
import com.azure.core.credential.KeyCredential;
import com.azure.core.util.BinaryData;
import com.onlinetoolhub.OnlineToolHub.service.ImageService;

import jakarta.annotation.PostConstruct;

/**
 * Implementation of {@link ImageService} that uses Azure Cognitive Services
 * and OpenAI to generate image captions, then packages the processed images
 * into a single ZIP file.
 */
@Service
public class ImageServiceImpl implements ImageService {

    private static final Logger logger = LoggerFactory.getLogger(ImageServiceImpl.class);

    @Value("${azure.cognitive.endpoint}")
    private String visualAiEndpoint;

    @Value("${azure.cognitive.subscriptionKey}")
    private String visualAiKey;

    @Value("${azure.openai.endpoint}")
    private String openAiEndpoint;

    @Value("${azure.openai.key}")
    private String openAiKey;

    /**
     * The ID or deployment name for the OpenAI model (e.g., GPT-3.5) used
     * to refine image captions.
     */
    private String deploymentOrModelId = "gpt-35-turbo-alttext-service";

    private OpenAIClient chatGpt;
    private ImageAnalysisClient visionClient;

    /**
     * Initializes the OpenAIClient and the ImageAnalysisClient after the bean is constructed.
     */
    @PostConstruct
    public void init() {
        // Initialize the OpenAIClient once
        chatGpt = new OpenAIClientBuilder()
                .endpoint(openAiEndpoint)
                .credential(new KeyCredential(openAiKey))
                .buildClient();

        // Initialize the ImageAnalysisClient once
        visionClient = new ImageAnalysisClientBuilder()
                .endpoint(visualAiEndpoint)
                .credential(new KeyCredential(visualAiKey))
                .buildClient();
    }

    /**
     * Fetches dense captions, tags, and text from Azure's Vision API, then
     * combines them into a prompt for OpenAI to generate a refined image caption.
     *
     * @param image the {@link MultipartFile} to be analyzed
     * @return a descriptive caption of the image
     * @throws IOException if an error occurs during file I/O
     */
    @Override
    public String getCaption(MultipartFile image) throws IOException {
        if (visualAiEndpoint == null || visualAiKey == null) {
            throw new IllegalStateException("Missing Azure Cognitive endpoint or subscription key.");
        }

        // Convert MultipartFile to a temporary File
        File tempFile = File.createTempFile("tempImage", ".png");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(image.getBytes());
        }

        try {
            // Analyze the image with Azure Vision API
            ImageAnalysisResult result = visionClient.analyze(
                BinaryData.fromFile(tempFile.toPath()),
                Arrays.asList(VisualFeatures.DENSE_CAPTIONS, VisualFeatures.TAGS, VisualFeatures.READ),
                new ImageAnalysisOptions().setGenderNeutralCaption(true)
            );

            // Parse and refine the analysis result into a final description
            return parseAnalysisResult(result);

        } catch (Exception e) {
            logger.error("Error during image analysis: {}", e.getMessage(), e);
            return "Error processing the image.";
        } finally {
            // Clean up the temporary file
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * Parses the image analysis result to extract captions, tags, and text,
     * then calls OpenAI to generate a final refined caption.
     *
     * @param result the result object from Azure Vision API
     * @return a refined, concise description of the image
     */
    private String parseAnalysisResult(ImageAnalysisResult result) {
        StringBuilder sb = new StringBuilder();

        double captionConfidenceThreshold = 0.5;
        double tagConfidenceThreshold = 0.65;

        // Filter captions
        List<DenseCaption> filteredCaptions = result.getDenseCaptions().getValues().stream()
            .filter(c -> c.getConfidence() >= captionConfidenceThreshold)
            .sorted((c1, c2) -> Double.compare(c2.getConfidence(), c1.getConfidence()))
            .collect(Collectors.toList());

        // Filter tags
        List<DetectedTag> filteredTags = result.getTags().getValues().stream()
            .filter(t -> t.getConfidence() >= tagConfidenceThreshold)
            .sorted((t1, t2) -> Double.compare(t2.getConfidence(), t1.getConfidence()))
            .collect(Collectors.toList());

        // Build analysis text with captions
        sb.append("Here are descriptions of different aspects of the image:\n");
        int count = 1;
        for (DenseCaption denseCaption : filteredCaptions) {
            sb.append(count++).append(". ").append(denseCaption.getText()).append("\n");
        }

        // Combine tags
        if (!filteredTags.isEmpty()) {
            sb.append("\nHere is a list of different parts of the image:\n");
            List<String> tags = filteredTags.stream().map(DetectedTag::getName).collect(Collectors.toList());
            sb.append(String.join(", ", tags));
        }

        // Extract text (e.g., brand names or other text)
        List<String> words = new ArrayList<>();
        if (result.getRead() != null && result.getRead().getBlocks() != null) {
            for (DetectedTextBlock block : result.getRead().getBlocks()) {
                for (DetectedTextLine line : block.getLines()) {
                    words.add(line.getText());
                }
            }
        }
        if (!words.isEmpty()) {
            sb.append("\n\nText found within the image:\n");
            sb.append(String.join(", ", words));
        }

        // Ensure prompt is not too large
        String analysisText = sb.toString();
        if (analysisText.length() > 3000) {
            analysisText = analysisText.substring(0, 3000);
        }

        // System message (guiding the AI)
        String systemMessage = "You are an AI assistant specialized in generating accurate, concise, " +
            "and descriptive captions for images based on provided visual descriptions, tags, and detected text. " +
            "Your goal is to create a single sentence that best represents the main content of the image.";

        // User prompt
        String promptText = "Based on the following information, generate a concise and descriptive " +
            "caption for the image in one sentence. Focus on the main content, include any brands or text " +
            "detected, and avoid adding any information not present in the data. Keep it under 200 characters.\n\n" +
            analysisText;

        // Create messages for the Chat Completions
        List<ChatRequestMessage> messages = new ArrayList<>();
        messages.add(new ChatRequestSystemMessage(systemMessage));
        messages.add(new ChatRequestUserMessage(promptText));

        // Configure OpenAI chat settings
        ChatCompletionsOptions options = new ChatCompletionsOptions(messages);
        options.setMaxTokens(200).setTemperature(0.5);

        // Request a response from OpenAI
        ChatCompletions chatCompletions = chatGpt.getChatCompletions(deploymentOrModelId, options);
        String finalCaption = chatCompletions.getChoices().get(0).getMessage().getContent().trim();

        // Truncate if needed
        return finalCaption.length() > 255 ? finalCaption.substring(0, 255) : finalCaption;
    }

    /**
     * Creates a ZIP file with processed image data. Each image will be renamed
     * using the provided captions (if valid), sanitized to avoid invalid filename characters.
     *
     * @param imageNames    a list of captions/names corresponding to each image
     * @param originalFiles the original image files
     * @return a byte array representing the contents of the ZIP file
     * @throws IOException if an I/O error occurs during ZIP creation
     */
    @Override
    public byte[] createZip(List<String> imageNames, List<MultipartFile> originalFiles) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (int i = 0; i < imageNames.size(); i++) {
                MultipartFile originalFile = originalFiles.get(i);
                if (originalFile != null) {
                    BufferedImage image = ImageIO.read(originalFile.getInputStream());
                    if (image != null) {
                        String sanitizedFileName = sanitizeFileName(imageNames.get(i));

                        ZipEntry entry = new ZipEntry(sanitizedFileName);
                        zos.putNextEntry(entry);

                        ByteArrayOutputStream imageBaos = new ByteArrayOutputStream();
                        ImageIO.write(image, "png", imageBaos);
                        zos.write(imageBaos.toByteArray());
                        zos.closeEntry();
                    } else {
                        logger.warn("Image is null for file: {}", imageNames.get(i));
                    }
                } else {
                    logger.warn("Original file is null for index: {}", i);
                }
            }
        }

        return baos.toByteArray();
    }

    /**
     * Sanitizes a filename by removing invalid characters, adjusting punctuation,
     * and limiting its length.
     *
     * @param filename the original filename or caption
     * @return a clean filename ready for use in the ZIP
     */
    private String sanitizeFileName(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "image.png";
        }

        // Trim whitespace and remove leading/trailing quotes
        String sanitized = filename.trim().replaceAll("^\"|\"$", "");

        // Replace punctuation between sentences with a comma
        sanitized = sanitized.replaceAll("[.!?]+(?=\\s|$)", ",");

        // Remove any trailing punctuation
        sanitized = sanitized.replaceAll("[,\\s]+$", "");

        // Replace other punctuation (like &, @, #, etc.) with spaces
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9\\s_-]", " ");

        // Collapse multiple spaces
        sanitized = sanitized.replaceAll("\\s+", " ").trim();

        // Fall back if empty
        if (sanitized.isEmpty()) {
            sanitized = "image";
        }

        // Limit filename length
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200).trim();
        }

        // Append PNG extension
        sanitized += ".png";

        return sanitized;
    }
}
