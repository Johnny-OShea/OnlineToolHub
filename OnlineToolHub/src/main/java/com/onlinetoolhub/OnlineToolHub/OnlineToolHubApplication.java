package com.onlinetoolhub.OnlineToolHub;

import org.modelmapper.ModelMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Main entry point for the Online Tool Hub application.
 */
@SpringBootApplication
public class OnlineToolHubApplication {

    /**
     * Creates and configures a {@link ModelMapper} bean used for object mapping.
     *
     * @return a configured instance of ModelMapper
     */
    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }

    /**
     * Main method that runs the Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(OnlineToolHubApplication.class, args);
    }
}