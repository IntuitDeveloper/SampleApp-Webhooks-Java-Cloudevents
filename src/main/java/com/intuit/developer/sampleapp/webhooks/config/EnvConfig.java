package com.intuit.developer.sampleapp.webhooks.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Configuration class to load environment variables from .env file
 * on application startup.
 */
@Configuration
public class EnvConfig {

    /**
     * Loads .env file and sets all variables as system properties
     * so Spring Boot can read them via ${VAR_NAME} syntax
     */
    @PostConstruct
    public void loadEnv() {
        // Load .env file from project root (if it exists)
        Dotenv dotenv = Dotenv.configure()
            .ignoreIfMissing()  // Don't fail if .env doesn't exist (for production)
            .load();
        
        // Set all .env variables as system properties
        dotenv.entries().forEach(entry -> 
            System.setProperty(entry.getKey(), entry.getValue())
        );
    }
}
