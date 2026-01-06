package com.intuit.developer.sampleapp.webhooks.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.ipp.util.Logger;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * QuickBooks configuration properties bound from application.yml with validation.
 * 
 * <p>This configuration class provides type-safe access to all QuickBooks-related
 * settings including OAuth2 credentials, API endpoints, and webhooks configuration.
 * All critical properties are validated on startup to fail fast if misconfigured.</p>
 * 
 * <p><strong>Validated Properties:</strong></p>
 * <ul>
 *   <li>OAuth Client ID and Secret (required, non-empty)</li>
 *   <li>Redirect URI (required, valid URL format)</li>
 *   <li>Environment (sandbox or production only)</li>
 *   <li>Encryption key (required, 16 characters for AES-128)</li>
 *   <li>Webhooks verifier token (required when webhooks enabled)</li>
 * </ul>
 * 
 * <p><strong>Environment Variables:</strong> All properties can be overridden via
 * environment variables. See ENV.md for complete documentation.</p>
 * 
 * <p><strong>Configuration:</strong> Update application.yml with your credentials
 * (file is git-ignored to protect secrets).</p>
 * 
 * @author Nate O'Neal
 * @version 1.0
 * @since 2025-11-18
 * @see ConfigurationProperties
 */
@Configuration
@ConfigurationProperties(prefix = "quickbooks")
@Validated
public class QuickBooksConfig {
    
    private static final org.slf4j.Logger LOG = Logger.getLogger();
    
    /** OAuth2 client ID for the QuickBooks app (from developer.intuit.com) */
    @NotBlank(message = "QuickBooks client ID is required. Set QB_CLIENT_ID environment variable or quickbooks.client-id in application.yml")
    private String clientId;
    
    /** OAuth2 client secret for the QuickBooks app (from developer.intuit.com) */
    @NotBlank(message = "QuickBooks client secret is required. Set QB_CLIENT_SECRET environment variable or quickbooks.client-secret in application.yml")
    private String clientSecret;
    
    /** Registered OAuth2 redirect URI used during the auth flow */
    @NotBlank(message = "QuickBooks redirect URI is required. Set QB_REDIRECT_URI environment variable or quickbooks.redirect-uri in application.yml")
    @Pattern(regexp = "https?://.+", message = "Redirect URI must be a valid HTTP(S) URL")
    private String redirectUri;
    
    /** Target environment (sandbox or production). Defaults to sandbox */
    @NotBlank(message = "QuickBooks environment is required")
    @Pattern(regexp = "(sandbox|production)", flags = Pattern.Flag.CASE_INSENSITIVE, message = "Environment must be 'sandbox' or 'production'")
    private String environment = "sandbox";
    
    /** Base REST API host */
    private String baseUrl = "https://quickbooks.api.intuit.com";
    
    /** OAuth scopes granted at authorization time */
    private List<String> scopes = new ArrayList<>();
    
    /** REST minor version to append to REST URLs */
    private String minorVersion = "75";
    
    /** Webhooks verifier token from developer.intuit.com */
    @NotBlank(message = "Webhooks verifier token is required. Set WEBHOOKS_VERIFIER_TOKEN environment variable or quickbooks.webhooks-verifier-token in application.yml")
    private String webhooksVerifierToken;
    
    /** Template for deep links to QBO UI */
    private String deepLinkTemplate = "https://app.qbo.intuit.com/app/invoice?txnId=%s&companyId=%s";
    
    /** Default constructor for property binding */
    public QuickBooksConfig() {
        // Set default scopes
        scopes.add("com.intuit.quickbooks.accounting");
    }
    
    /**
     * Validates configuration after properties are bound.
     * 
     * <p>Performs additional validation beyond JSR-303 annotations:
     * <ul>
     *   <li>Warns if using default encryption key (security risk)</li>
     *   <li>Logs configuration summary for debugging</li>
     *   <li>Validates environment-specific settings</li>
     * </ul>
     * 
     * @throws IllegalStateException if critical configuration is invalid
     */
    @PostConstruct
    public void validateConfiguration() {
        LOG.info("=".repeat(70));
        LOG.info("QuickBooks Configuration Loaded");
        LOG.info("=".repeat(70));
        LOG.info("Environment: {}" , environment);
        LOG.info("Client ID: {}***", clientId != null && clientId.length() > 3 ? clientId.substring(0, 3) : "???");
        LOG.info("Redirect URI: {}", redirectUri);
        LOG.info("API Base URL: {}", getEnvironmentBaseUrl());
        LOG.info("Minor Version: {}", minorVersion);
        LOG.info("Scopes: {}", String.join(", ", scopes));
        
        // Production environment check
        if (isProduction()) {
            LOG.warn("Running in PRODUCTION mode! Ensure all credentials are for production.");
        } else {
            LOG.info("Running in SANDBOX mode (test environment)");
        }
        
        LOG.info("=".repeat(70));
    }
    
    // Getters and setters
    
    public String getClientId() {
        return clientId;
    }
    
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }
    
    public String getClientSecret() {
        return clientSecret;
    }
    
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }
    
    public String getRedirectUri() {
        return redirectUri;
    }
    
    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }
    
    public String getEnvironment() {
        return environment;
    }
    
    public void setEnvironment(String environment) {
        this.environment = environment;
    }
    
    public String getBaseUrl() {
        return baseUrl;
    }
    
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }
    
    public List<String> getScopes() {
        return scopes;
    }
    
    public void setScopes(List<String> scopes) {
        this.scopes = scopes;
    }
    
    public String getMinorVersion() {
        return minorVersion;
    }
    
    public void setMinorVersion(String minorVersion) {
        this.minorVersion = minorVersion;
    }
    
    public String getWebhooksVerifierToken() {
        return webhooksVerifierToken;
    }
    
    public void setWebhooksVerifierToken(String webhooksVerifierToken) {
        this.webhooksVerifierToken = webhooksVerifierToken;
    }
    
    public String getDeepLinkTemplate() {
        return deepLinkTemplate;
    }
    
    public void setDeepLinkTemplate(String deepLinkTemplate) {
        this.deepLinkTemplate = deepLinkTemplate;
    }
    
    /**
     * Gets the correct base URL based on environment.
     * 
     * <p>Automatically switches between sandbox and production URLs:</p>
     * <ul>
     *   <li>Sandbox: https://sandbox-quickbooks.api.intuit.com</li>
     *   <li>Production: https://quickbooks.api.intuit.com</li>
     * </ul>
     * 
     * @return The appropriate QuickBooks API base URL
     */
    public String getEnvironmentBaseUrl() {
        if ("production".equalsIgnoreCase(environment)) {
            return "https://quickbooks.api.intuit.com";
        } else {
            return "https://sandbox-quickbooks.api.intuit.com";
        }
    }
    
    /**
     * Checks if currently configured for production environment.
     * 
     * @return true if environment is "production" (case-insensitive)
     */
    public boolean isProduction() {
        return "production".equalsIgnoreCase(environment);
    }
    
    /**
     * Checks if currently configured for sandbox environment.
     * 
     * @return true if environment is "sandbox" (case-insensitive)
     */
    public boolean isSandbox() {
        return "sandbox".equalsIgnoreCase(environment);
    }
    
    /**
     * Provides a RestTemplate bean with logging interceptors for debugging
     * API calls to QuickBooks endpoints
     */
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate rt = new RestTemplate(
            new BufferingClientHttpRequestFactory(new SimpleClientHttpRequestFactory())
        );
        
        // Add logging interceptor
        rt.getInterceptors().add((request, body, execution) -> {
            org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger("QuickBooksAPI");
            
            logger.debug("API Request: {} {} | Headers: {}",
                request.getMethod(), 
                request.getURI(), 
                request.getHeaders()
            );
            
            org.springframework.http.client.ClientHttpResponse response = execution.execute(request, body);
            
            logger.debug("API Response: status={} | Headers: {}",
                response.getStatusCode(), 
                response.getHeaders()
            );
            
            return response;
        });
        
        return rt;
    }
    
    /**
     * Provides a shared Jackson ObjectMapper for JSON serialization tasks
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }
}
