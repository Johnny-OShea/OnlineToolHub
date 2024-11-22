package com.onlinetoolhub.OnlineToolHub;

import org.modelmapper.ModelMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Main class for the Alt Text application.
 */
@SpringBootApplication
public class OnlineToolHubApplication {

    /**
     * Returns the ModelMapper bean.
     * @return the ModelMapper for the application.
     */
    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }

    /**
     * Starts the Alt Text application.
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(OnlineToolHubApplication.class, args);
    }

}

