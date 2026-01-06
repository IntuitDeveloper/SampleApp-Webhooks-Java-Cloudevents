package com.intuit.developer.sampleapp.webhooks;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application entry point for QuickBooks Webhooks Sample App.
 * 
 * <p>Environment variables are loaded from .env file via EnvConfig.java</p>
 * 
 * @author Nate O'Neal
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
    
}
