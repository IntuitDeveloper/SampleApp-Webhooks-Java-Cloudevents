package com.intuit.developer.sampleapp.webhooks.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.developer.sampleapp.webhooks.config.QuickBooksConfig;

/**
 * Service for managing Intuit Refresh Token Hard Expiration (5-year max lifetime).
 *
 * <p>Implements the logic described in the Refresh Token Hard Expiration PRD:
 * <ul>
 *   <li>Step 1: Token API enhancement with x-include-refresh-token-hard-expires-in header</li>
 *   <li>Step 2: Calculated expiry fallback for legacy clients</li>
 *   <li>Step 3: Reconnect URL validation via Intuit proxy</li>
 *   <li>Step 4: Notification timeline awareness (30-day, 7-day, expiration-day)</li>
 * </ul>
 */
@Service
public class RefreshTokenHardExpirationService {

    private static final Logger logger = LoggerFactory.getLogger(RefreshTokenHardExpirationService.class);

    /** Maximum refresh token lifetime: 5 years in seconds */
    public static final long MAX_LIFETIME_SECONDS = 5L * 365 * 24 * 60 * 60; // 157,680,000

    /** 30-day warning threshold in seconds */
    public static final long THIRTY_DAY_THRESHOLD_SECONDS = 30L * 24 * 60 * 60;

    /** 7-day warning threshold in seconds */
    public static final long SEVEN_DAY_THRESHOLD_SECONDS = 7L * 24 * 60 * 60;

    /** Header to request the hard expiration field in the response */
    public static final String HARD_EXPIRES_REQUEST_HEADER = "x-include-refresh-token-hard-expires-in";

    /** Response field name for hard expiration seconds */
    public static final String HARD_EXPIRES_RESPONSE_FIELD = "x_refresh_token_hard_expires_in";

    /** Standard response field for refresh token expiry */
    public static final String REFRESH_TOKEN_EXPIRES_IN_FIELD = "x_refresh_token_expires_in";

    /** Intuit proxy reconnect URL pattern */
    public static final String RECONNECT_URL_PATTERN =
            "https://appcenter.intuit.com/app/connect/oauth2/request?appId=%s&realmId=%s&mode=reconnect";

    /** Pattern to validate reconnect URLs go through the Intuit proxy */
    private static final Pattern RECONNECT_URL_VALIDATOR = Pattern.compile(
            "^https://appcenter\\.intuit\\.com/app/connect/oauth2/request\\?.*mode=reconnect.*$"
    );

    @Autowired
    private QuickBooksConfig config;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    // ========== Step 1: Token API Enhancement ==========

    /**
     * Refreshes a token with the x-include-refresh-token-hard-expires-in header
     * to retrieve the hard expiration field from the Intuit token endpoint.
     *
     * @param refreshToken the current refresh token
     * @return map containing token fields including x_refresh_token_hard_expires_in
     */
    public Map<String, Object> refreshTokenWithHardExpiration(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token must not be null or blank");
        }

        String tokenEndpoint = getTokenEndpoint();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(config.getClientId(), config.getClientSecret());
        headers.set(HARD_EXPIRES_REQUEST_HEADER, "true");

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);

        logger.info("Requesting token refresh with hard-expiration header from {}", tokenEndpoint);

        ResponseEntity<String> response = restTemplate.postForEntity(tokenEndpoint, request, String.class);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> tokenResponse = objectMapper.readValue(response.getBody(), Map.class);
            logger.info("Token response received. Contains hard_expires_in: {}",
                    tokenResponse.containsKey(HARD_EXPIRES_RESPONSE_FIELD));
            return tokenResponse;
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse token response: " + e.getMessage(), e);
        }
    }

    /**
     * Validates that the token response contains the hard expiration field and
     * that its value is a positive number of seconds within the 5-year max.
     *
     * @param tokenResponse the parsed token response map
     * @return true if x_refresh_token_hard_expires_in is present and valid
     */
    public boolean validateHardExpirationField(Map<String, Object> tokenResponse) {
        if (tokenResponse == null || !tokenResponse.containsKey(HARD_EXPIRES_RESPONSE_FIELD)) {
            return false;
        }

        Object value = tokenResponse.get(HARD_EXPIRES_RESPONSE_FIELD);
        if (value == null) {
            return false;
        }

        try {
            long hardExpiresIn = ((Number) value).longValue();
            return hardExpiresIn > 0 && hardExpiresIn <= MAX_LIFETIME_SECONDS;
        } catch (ClassCastException e) {
            logger.warn("x_refresh_token_hard_expires_in is not a number: {}", value);
            return false;
        }
    }

    /**
     * Extracts the hard expiration seconds from a token response.
     *
     * @param tokenResponse the parsed token response map
     * @return remaining seconds until hard expiration, or -1 if not present
     */
    public long getHardExpirationSeconds(Map<String, Object> tokenResponse) {
        if (!validateHardExpirationField(tokenResponse)) {
            return -1;
        }
        return ((Number) tokenResponse.get(HARD_EXPIRES_RESPONSE_FIELD)).longValue();
    }

    // ========== Step 2: Calculated Expiry (Legacy Fallback) ==========

    /**
     * Determines if a refresh token has hit its hard expiration by comparing
     * old and new expiry dates after a token refresh.
     *
     * <p>Logic: If the token is within 30 days of the standard
     * x_refresh_token_expires_in date and the old and new expiry dates are
     * identical, the token has reached its 5-year hard limit.</p>
     *
     * @param oldExpiryEpochSeconds the expiry epoch of the old refresh token
     * @param newExpiryEpochSeconds the expiry epoch of the new refresh token
     * @return true if the token has hit its hard expiration ceiling
     */
    public boolean isHardExpired(long oldExpiryEpochSeconds, long newExpiryEpochSeconds) {
        return oldExpiryEpochSeconds == newExpiryEpochSeconds;
    }

    /**
     * Checks whether a refresh token is within the 30-day proximity window
     * of its standard expiry, which is the trigger window for hard-expiration
     * detection.
     *
     * @param refreshTokenExpiresIn seconds until the refresh token expires
     * @return true if within 30 days of expiry
     */
    public boolean isWithin30DayWindow(long refreshTokenExpiresIn) {
        return refreshTokenExpiresIn > 0 && refreshTokenExpiresIn <= THIRTY_DAY_THRESHOLD_SECONDS;
    }

    /**
     * Calculates the approximate original token creation instant based on
     * the current remaining hard expiration seconds.
     *
     * @param hardExpiresInSeconds remaining seconds from hard expiration response
     * @return the estimated creation instant of the refresh token
     */
    public Instant estimateTokenCreationDate(long hardExpiresInSeconds) {
        return Instant.now().minus(Duration.ofSeconds(MAX_LIFETIME_SECONDS - hardExpiresInSeconds));
    }

    /**
     * Full legacy-fallback expiry check: attempts a token refresh within the
     * 30-day window and compares old vs. new expiry to detect hard expiration.
     *
     * @param currentRefreshTokenExpiresIn current x_refresh_token_expires_in in seconds
     * @param oldExpiryEpochSeconds epoch seconds of the current token's expiry
     * @param newExpiryEpochSeconds epoch seconds of the refreshed token's expiry
     * @return true if the developer should guide the user to re-auth
     */
    public boolean requiresReAuth(long currentRefreshTokenExpiresIn,
                                  long oldExpiryEpochSeconds,
                                  long newExpiryEpochSeconds) {
        if (!isWithin30DayWindow(currentRefreshTokenExpiresIn)) {
            return false;
        }
        return isHardExpired(oldExpiryEpochSeconds, newExpiryEpochSeconds);
    }

    // ========== Step 3: Reconnect URL Integration ==========

    /**
     * Builds the Intuit-proxied reconnect URL for a given app and realm.
     *
     * @param appId the 3P application ID
     * @param realmId the QuickBooks company realm ID
     * @return the secure reconnect URL routed through Intuit's proxy
     */
    public String buildReconnectUrl(String appId, String realmId) {
        if (appId == null || appId.isBlank()) {
            throw new IllegalArgumentException("App ID must not be null or blank");
        }
        if (realmId == null || realmId.isBlank()) {
            throw new IllegalArgumentException("Realm ID must not be null or blank");
        }
        return String.format(RECONNECT_URL_PATTERN, appId, realmId);
    }

    /**
     * Validates that a reconnect URL goes through the Intuit proxy asset to
     * prevent malicious URL injection.
     *
     * @param reconnectUrl the URL to validate
     * @return true if the URL matches the required Intuit proxy pattern
     */
    public boolean isValidReconnectUrl(String reconnectUrl) {
        if (reconnectUrl == null || reconnectUrl.isBlank()) {
            return false;
        }
        return RECONNECT_URL_VALIDATOR.matcher(reconnectUrl).matches();
    }

    // ========== Step 4: Notification Timeline ==========

    /**
     * Notification severity levels corresponding to the expiration timeline.
     */
    public enum NotificationLevel {
        /** More than 30 days remaining — no notification needed */
        NONE,
        /** T-minus 30 days — App Card Warning/Task (IPN) */
        WARNING_30_DAY,
        /** T-minus 7 days — Email to Primary Admin + connecting user */
        CRITICAL_7_DAY,
        /** Expiration day — App Card Error, data sync stopped */
        EXPIRED
    }

    /**
     * Determines the notification level based on remaining hard-expiration seconds.
     *
     * @param hardExpiresInSeconds remaining seconds until hard expiration
     * @return the appropriate {@link NotificationLevel}
     */
    public NotificationLevel getNotificationLevel(long hardExpiresInSeconds) {
        if (hardExpiresInSeconds <= 0) {
            return NotificationLevel.EXPIRED;
        }
        if (hardExpiresInSeconds <= SEVEN_DAY_THRESHOLD_SECONDS) {
            return NotificationLevel.CRITICAL_7_DAY;
        }
        if (hardExpiresInSeconds <= THIRTY_DAY_THRESHOLD_SECONDS) {
            return NotificationLevel.WARNING_30_DAY;
        }
        return NotificationLevel.NONE;
    }

    /**
     * Checks whether data sync should be stopped (token has expired).
     *
     * @param hardExpiresInSeconds remaining seconds until hard expiration
     * @return true if the token is expired and sync must stop
     */
    public boolean shouldStopDataSync(long hardExpiresInSeconds) {
        return hardExpiresInSeconds <= 0;
    }

    /**
     * Checks whether an email notification should be sent (7-day threshold).
     *
     * @param hardExpiresInSeconds remaining seconds until hard expiration
     * @return true if within 7-day email notification window
     */
    public boolean shouldSendEmailNotification(long hardExpiresInSeconds) {
        return hardExpiresInSeconds > 0 && hardExpiresInSeconds <= SEVEN_DAY_THRESHOLD_SECONDS;
    }

    /**
     * Checks whether an In-Product Notification (IPN) should appear (30-day threshold).
     *
     * @param hardExpiresInSeconds remaining seconds until hard expiration
     * @return true if within 30-day IPN window
     */
    public boolean shouldShowInProductNotification(long hardExpiresInSeconds) {
        return hardExpiresInSeconds > 0 && hardExpiresInSeconds <= THIRTY_DAY_THRESHOLD_SECONDS;
    }

    // ========== Helpers ==========

    private String getTokenEndpoint() {
        if (config.isProduction()) {
            return "https://oauth.platform.intuit.com/oauth2/v1/tokens/bearer";
        }
        return "https://oauth.platform.intuit.com/oauth2/v1/tokens/bearer";
    }
}
