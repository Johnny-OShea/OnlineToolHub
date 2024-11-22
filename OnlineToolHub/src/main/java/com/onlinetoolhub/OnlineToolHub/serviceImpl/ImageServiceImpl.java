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

    private String deploymentOrModelId = "gpt-35-turbo-alttext-service";

    private OpenAIClient chatGpt;
    private ImageAnalysisClient visionClient;

    @PostConstruct
    public void init() {
        // Initialize the OpenAIClient once during bean initialization
        chatGpt = new OpenAIClientBuilder().endpoint(openAiEndpoint).credential(new KeyCredential(openAiKey))
                .buildClient();

        // Initialize the ImageAnalysisClient once during bean initialization
        visionClient = new ImageAnalysisClientBuilder().endpoint(visualAiEndpoint)
                .credential(new KeyCredential(visualAiKey)).buildClient();
    }

    /**
     * This method fetches dense captions, tags, and brands from Azure's Vision API,
     * combines them, and uses OpenAI to generate a refined image description.
     *
     * @param image
     *        The MultipartFile image to analyze.
     * @return A descriptive caption of the image.
     * @throws IOException
     *         if an error occurs during the process.
     */
    @Override
    public String getCaption(MultipartFile image) throws IOException {
        // Ensure that the endpoint and key are available
        if (visualAiEndpoint == null || visualAiKey == null) {
            throw new IllegalStateException("Missing Azure Cognitive endpoint or subscription key.");
        }

        // Convert MultipartFile to a temporary File
        File tempFile = File.createTempFile("tempImage", ".png");
        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            fos.write(image.getBytes());
        }

        try {
            // Generate captions, tags, and brands for the input image
            ImageAnalysisResult result = visionClient.analyze(BinaryData.fromFile(tempFile.toPath()),
                    Arrays.asList(VisualFeatures.DENSE_CAPTIONS, VisualFeatures.TAGS, VisualFeatures.READ),
                    new ImageAnalysisOptions().setGenderNeutralCaption(true));

            // Parse the analysis result and return a refined description
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
     * Parses the ImageAnalysisResult to extract captions, tags, categories, and
     * brands, and uses OpenAI to generate a refined image description.
     *
     * @param result
     *        The result from the image analysis.
     * @return A refined image description.
     */
    private String parseAnalysisResult(ImageAnalysisResult result) {
        StringBuilder sb = new StringBuilder();

        // Set confidence thresholds
        double captionConfidenceThreshold = 0.5;
        double tagConfidenceThreshold = 0.65;

        // Filter and sort captions
        List<DenseCaption> filteredCaptions = result.getDenseCaptions().getValues().stream()
                .filter(caption -> caption.getConfidence() >= captionConfidenceThreshold)
                .sorted((c1, c2) -> Double.compare(c2.getConfidence(), c1.getConfidence()))
                .collect(Collectors.toList());

        // Filter and sort tags
        List<DetectedTag> filteredTags = result.getTags().getValues().stream()
                .filter(tag -> tag.getConfidence() >= tagConfidenceThreshold)
                .sorted((t1, t2) -> Double.compare(t2.getConfidence(), t1.getConfidence()))
                .collect(Collectors.toList());

        sb.append("Here are descriptions of different aspects of the image:\n");
        int count = 1;
        for (DenseCaption denseCaption : filteredCaptions) {
            sb.append(count++).append(". ").append(denseCaption.getText()).append("\n");
            System.out.println(denseCaption.getText() + " " + denseCaption.getConfidence());
        }

        // Extract tags
        List<String> tags = new ArrayList<>();
        for (DetectedTag tag : filteredTags) {
            tags.add(tag.getName());
            System.out.println(tag.getName() + ": " + tag.getConfidence());
        }

        // Figure out what words existed
        List<String> words = new ArrayList<>();
        for (DetectedTextBlock currentBlock : result.getRead().getBlocks()) {
            for (DetectedTextLine line : currentBlock.getLines()) {
                // We will add sentences at a time
                words.add(line.getText());
                System.out.println(line.getText());
            }
        }

        // Combine tags
        sb.append("\nHere is a list of different parts of the image:\n");
        sb.append(String.join(", ", tags));

        // Combine any text (specifically looking for brands)
        if (!words.isEmpty()) {
            sb.append("\n\nText found within the image:\n");
            sb.append(String.join(", ", words));
        }

        String analysisText = sb.toString();

        // Ensure input text is not overly long
        if (analysisText.length() > 3000) {
            analysisText = analysisText.substring(0, 3000);
        }

        // Modify system message
        String systemMessage = "You are an AI assistant specialized in generating accurate, concise, and descriptive captions for images based on provided visual descriptions, tags, and detected text. Your goal is to create a single sentence that best represents the main content of the image.";

        // Construct the prompt
        String promptText = "Based on the following information, generate a concise and descriptive caption for the image in one sentence. Focus on the main content, include any brands or text detected, and avoid adding any information not present in the data. Keep it under 200 characters.\n\n"
                + analysisText;

        // Create the chat messages
        List<ChatRequestMessage> messages = new ArrayList<>();
        messages.add(new ChatRequestSystemMessage(systemMessage));
        messages.add(new ChatRequestUserMessage(promptText));

        // Set up the options
        ChatCompletionsOptions options = new ChatCompletionsOptions(messages);
        options.setMaxTokens(200).setTemperature(.5);

        // Get the response
        ChatCompletions chatCompletions = chatGpt.getChatCompletions(deploymentOrModelId, options);

        String finalCaption = chatCompletions.getChoices().get(0).getMessage().getContent().trim();

        // Truncate if necessary
        finalCaption = finalCaption.length() > 255 ? finalCaption.substring(0, 255) : finalCaption;

        return finalCaption;
    }

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
     * Sanitizes the filename by removing invalid characters, adjusting punctuation,
     * and limiting its length.
     *
     * @param filename
     *        The original filename.
     * @return The sanitized filename.
     */
    private String sanitizeFileName(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "image.png";
        }

        // Step 1: Trim whitespace and remove any leading and trailing quotes
        String sanitized = filename.trim().replaceAll("^\"|\"$", "");

        // Step 2: Replace sequences of punctuation between sentences with a single
        // comma
        sanitized = sanitized.replaceAll("[.!?]+(?=\\s|$)", ",");

        // Step 3: Remove any remaining punctuation at the end of the filename
        sanitized = sanitized.replaceAll("[,\\s]+$", "");

        // Step 4: Replace other punctuation (like &, @, #, etc.) with spaces
        sanitized = sanitized.replaceAll("[^a-zA-Z0-9\\s_-]", " ");

        // Step 5: Replace multiple spaces or commas with a single space
        sanitized = sanitized.replaceAll("\\s+", " ").replaceAll(",+", ",").trim();

        // Step 6: Ensure the filename is not empty
        if (sanitized.isEmpty()) {
            sanitized = "image";
        }

        // Step 7: Limit filename length to 200 characters
        if (sanitized.length() > 200) {
            sanitized = sanitized.substring(0, 200).trim();
        }

        // Step 8: Append ".png" extension
        sanitized += ".png";

        return sanitized;
    }

}
