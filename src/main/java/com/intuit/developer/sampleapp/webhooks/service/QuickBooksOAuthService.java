package com.intuit.developer.sampleapp.webhooks.service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.intuit.developer.sampleapp.webhooks.config.QuickBooksConfig;
import com.intuit.oauth2.client.OAuth2PlatformClient;
import com.intuit.oauth2.config.Environment;
import com.intuit.oauth2.config.OAuth2Config;
import com.intuit.oauth2.config.Scope;
import com.intuit.oauth2.data.BearerTokenResponse;
import com.intuit.oauth2.exception.ConnectionException;
import com.intuit.oauth2.exception.InvalidRequestException;
import com.intuit.oauth2.exception.OAuthException;

/**
 * Enhanced OAuth 2.0 service for QuickBooks authentication
 * 
 * Features:
 * - Discovery API for automatic endpoint resolution
 * - Comprehensive error handling with user-friendly messages
 * - Token refresh management
 * - Secure state generation
 * 
 * Based on best practices from SampleApp-Dimensions-Java
 */
@Service
public class QuickBooksOAuthService {
    
    private static final Logger logger = LoggerFactory.getLogger(QuickBooksOAuthService.class);
    
    @Autowired
    private QuickBooksConfig config;
    
    @Autowired
    private RefreshTokenHardExpirationService hardExpirationService;
    
    private final SecureRandom secureRandom = new SecureRandom();
    
    /**
     * Builds an authorization URL using the SDK discovery API and configured
     * scopes, environment and redirect URI
     */
    public String getAuthorizationUrl() {
        return getAuthorizationUrl(false);
    }
    
    /**
     * Builds an authorization URL with an option to force the IdP login screen
     * When forceLogin is true, we add prompt=login consent to ensure the user can
     * pick a different company (and re-enter credentials if needed)
     */
    public String getAuthorizationUrl(boolean forceLogin) {
        try {
            // Validate configuration before proceeding
            validateOAuthConfiguration();
            
            String state = generateState();
            
            OAuth2Config oauth2Config = new OAuth2Config.OAuth2ConfigBuilder(
                config.getClientId(),
                config.getClientSecret()
            )
            .callDiscoveryAPI(getEnvironment())  // Auto-discover endpoints!
            .buildConfig();
            
            logger.debug("OAuth scopes used: {}", config.getScopes());
            logger.debug("OAuth environment: {} | redirectUri: {}", config.getEnvironment(), config.getRedirectUri());
            
            // Convert string scopes to Scope enum - just use Accounting scope
            List<Scope> scopeList = new ArrayList<>();
            scopeList.add(Scope.Accounting);
            
            String authUrl = oauth2Config.prepareUrl(
                scopeList,
                config.getRedirectUri(),
                state
            );
            
            // Force re-consent so user can choose a different company during OAuth
            String prompt = forceLogin ? "login%20consent" : "consent";
            if (authUrl.contains("?")) {
                authUrl += "&prompt=" + prompt;
            } else {
                authUrl += "?prompt=" + prompt;
            }
            
            return authUrl;
            
        } catch (InvalidRequestException e) {
            // Handle any OAuth or network errors
            logger.error("Error generating OAuth URL: {}", e.getMessage());
            throw new RuntimeException("Unable to generate OAuth URL: " + e.getMessage());
        }
    }
    
    /**
     * Revokes the given refresh token using the OAuth2 SDK
     * This helps ensure the next login starts fresh and allows switching companies
     */
    public void revokeToken(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) {
            return;
        }
        try {
            validateOAuthConfiguration();
            OAuth2Config oauth2Config = new OAuth2Config.OAuth2ConfigBuilder(
                config.getClientId(),
                config.getClientSecret()
            )
            .callDiscoveryAPI(getEnvironment())
            .buildConfig();
            
            OAuth2PlatformClient client = new OAuth2PlatformClient(oauth2Config);
            client.revokeToken(refreshToken);
            logger.info("Refresh token revoked successfully");
        } catch (ConnectionException e) {
            // Don't block logout on revoke failures; just log
            logger.warn("Failed to revoke refresh token: {}", e.getMessage());
        }
    }
    
    /**
     * Exchanges an authorization code for access and refresh tokens using the SDK
     * Returns a map with access_token, refresh_token, expires_in, realm_id
     */
    public Map<String, Object> exchangeCodeForToken(String authCode, String realmId) {
        try {
            // Validate required parameters
            if (authCode == null || authCode.trim().isEmpty()) {
                throw new RuntimeException("Authorization code is required");
            }
            if (realmId == null || realmId.trim().isEmpty()) {
                throw new RuntimeException("Realm ID is required");
            }
            
            validateOAuthConfiguration();
            
            OAuth2Config oauth2Config = new OAuth2Config.OAuth2ConfigBuilder(
                config.getClientId(),
                config.getClientSecret()
            )
            .callDiscoveryAPI(getEnvironment())
            .buildConfig();
            
            OAuth2PlatformClient client = new OAuth2PlatformClient(oauth2Config);
            
            BearerTokenResponse bearerTokenResponse = client.retrieveBearerTokens(
                authCode,
                config.getRedirectUri()
            );
            
            Map<String, Object> result = new HashMap<>();
            result.put("access_token", bearerTokenResponse.getAccessToken());
            result.put("refresh_token", bearerTokenResponse.getRefreshToken());
            result.put("expires_in", bearerTokenResponse.getExpiresIn());
            result.put("realm_id", realmId);
            
            logger.info("Successfully exchanged authorization code for tokens. Realm ID: {}", realmId);
            
            return result;
            
        } catch (OAuthException e) {
            // Handle specific OAuth SDK errors
            String errorMessage = e.getMessage().toLowerCase();
            if (errorMessage.contains("invalid_grant") || errorMessage.contains("authorization_code")) {
                throw new RuntimeException("Invalid or expired authorization code: " + e.getMessage());
            } else if (errorMessage.contains("invalid_client") || errorMessage.contains("client_id")) {
                throw new RuntimeException("Invalid client credentials: " + e.getMessage());
            } else if (errorMessage.contains("invalid_scope")) {
                throw new RuntimeException("Invalid scope: " + e.getMessage());
            } else {
                throw new RuntimeException("OAuth token exchange failed: " + e.getMessage());
            }
        } catch (RuntimeException e) {
            // Handle network or other unexpected errors
            if (e.getMessage().contains("network") || e.getMessage().contains("connection") || e.getMessage().contains("timeout")) {
                throw new RuntimeException("Network error during token exchange: " + e.getMessage());
            } else {
                throw new RuntimeException("Unexpected error during token exchange: " + e.getMessage());
            }
        }
    }
    
    /**
     * Refreshes the access token using the SDK and returns a map with
     * access_token, refresh_token (possibly rotated) and expires_in
     */
    public Map<String, Object> refreshToken(String refreshToken) {
        try {
            // Validate required parameters
            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                throw new RuntimeException("Refresh token is required");
            }
            
            validateOAuthConfiguration();
            
            OAuth2Config oauth2Config = new OAuth2Config.OAuth2ConfigBuilder(
                config.getClientId(),
                config.getClientSecret()
            )
            .callDiscoveryAPI(getEnvironment())
            .buildConfig();
            
            OAuth2PlatformClient client = new OAuth2PlatformClient(oauth2Config);
            
            BearerTokenResponse bearerTokenResponse = client.refreshToken(refreshToken);
            
            Map<String, Object> result = new HashMap<>();
            result.put("access_token", bearerTokenResponse.getAccessToken());
            result.put("refresh_token", bearerTokenResponse.getRefreshToken() != null ? 
                bearerTokenResponse.getRefreshToken() : refreshToken);
            result.put("expires_in", bearerTokenResponse.getExpiresIn());
            
            logger.info("Successfully refreshed access token");
            
            return result;
            
        } catch (OAuthException e) {
            // Handle specific OAuth SDK errors
            String errorMessage = e.getMessage().toLowerCase();
            if (errorMessage.contains("invalid_grant") || errorMessage.contains("refresh_token")) {
                throw new RuntimeException("Invalid or expired refresh token: " + e.getMessage());
            } else if (errorMessage.contains("invalid_client")) {
                throw new RuntimeException("Invalid client credentials during token refresh: " + e.getMessage());
            } else {
                throw new RuntimeException("Token refresh failed: " + e.getMessage());
            }
        } catch (RuntimeException e) {
            // Handle network or other unexpected errors
            if (e.getMessage().contains("network") || e.getMessage().contains("connection") || e.getMessage().contains("timeout")) {
                throw new RuntimeException("Network error during token refresh: " + e.getMessage());
            } else {
                throw new RuntimeException("Unexpected error during token refresh: " + e.getMessage());
            }
        }
    }
    
    /**
     * Refreshes the access token while requesting the hard expiration field.
     * Sends the x-include-refresh-token-hard-expires-in: true header so the
     * response includes x_refresh_token_hard_expires_in (seconds until the
     * 5-year absolute lifetime expires).
     *
     * @param refreshToken the current refresh token
     * @return map with access_token, refresh_token, expires_in and
     *         x_refresh_token_hard_expires_in (if supported)
     */
    public Map<String, Object> refreshTokenWithHardExpiration(String refreshToken) {
        try {
            if (refreshToken == null || refreshToken.trim().isEmpty()) {
                throw new RuntimeException("Refresh token is required");
            }
            validateOAuthConfiguration();

            // Delegate to the dedicated hard-expiration service which sends the
            // custom header via RestTemplate (SDK does not support this header yet)
            return hardExpirationService.refreshTokenWithHardExpiration(refreshToken);

        } catch (RuntimeException e) {
            logger.error("Error refreshing token with hard-expiration header: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Generates a cryptographically-strong random state parameter for the OAuth
     * authorization request
     */
    private String generateState() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    
    /** Resolves the SDK Environment enum from configured string */
    private Environment getEnvironment() {
        return "production".equalsIgnoreCase(config.getEnvironment()) ? 
            Environment.PRODUCTION : Environment.SANDBOX;
    }
    
    /**
     * Validates that required OAuth configuration is present and structurally
     * correct before contacting QuickBooks
     */
    private void validateOAuthConfiguration() {
        if (config.getClientId() == null || config.getClientId().trim().isEmpty()) {
            throw new RuntimeException("Client ID is required but not configured");
        }
        
        if (config.getClientSecret() == null || config.getClientSecret().trim().isEmpty()) {
            throw new RuntimeException("Client Secret is required but not configured");
        }
        
        if (config.getRedirectUri() == null || config.getRedirectUri().trim().isEmpty()) {
            throw new RuntimeException("Redirect URI is required but not configured");
        }
        
        // Validate redirect URI format
        String redirectUri = config.getRedirectUri();
        if (!redirectUri.startsWith("http://") && !redirectUri.startsWith("https://")) {
            throw new RuntimeException("Redirect URI must start with http:// or https://");
        }
        
        // Validate environment
        String environment = config.getEnvironment();
        if (environment == null || (!"sandbox".equalsIgnoreCase(environment) && !"production".equalsIgnoreCase(environment))) {
            throw new RuntimeException("Environment must be 'sandbox' or 'production'");
        }
    }
}
