package com.intuit.developer.sampleapp.webhooks.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.developer.sampleapp.webhooks.config.QuickBooksConfig;
import com.intuit.developer.sampleapp.webhooks.service.RefreshTokenHardExpirationService.NotificationLevel;

/**
 * Comprehensive test suite for Refresh Token Hard Expiration (5-year max lifetime).
 *
 * <p>Validates the four steps outlined in the Hard Expiration QA prompt:</p>
 * <ol>
 *   <li><strong>Step 1</strong> – Token API Enhancement: x-include-refresh-token-hard-expires-in header
 *       and x_refresh_token_hard_expires_in response field</li>
 *   <li><strong>Step 2</strong> – Developer Logic (Calculated Expiry): legacy fallback comparing
 *       old vs. new refresh token expiry dates</li>
 *   <li><strong>Step 3</strong> – Reconnect URL Integration: Intuit proxy validation and
 *       malicious URL injection prevention</li>
 *   <li><strong>Step 4</strong> – Notification Timeline: 30-day IPN, 7-day email, expiration-day
 *       error checks</li>
 * </ol>
 *
 * @see RefreshTokenHardExpirationService
 */
@ExtendWith(MockitoExtension.class)
public class RefreshTokenHardExpirationTest {

    @Mock
    private QuickBooksConfig config;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private RefreshTokenHardExpirationService service;

    private static final String SAMPLE_REFRESH_TOKEN = "AB11234567890abcdef";
    private static final String SAMPLE_CLIENT_ID = "ABcSKl3ng5RTgH";
    private static final String SAMPLE_CLIENT_SECRET = "p9F5gR2sE7hL1mN8";
    private static final long FIVE_YEARS_SECONDS = 5L * 365 * 24 * 60 * 60; // 157,680,000

    @BeforeEach
    void setUp() {
        // Common stubs used by methods that hit the token endpoint
        lenient().when(config.getClientId()).thenReturn(SAMPLE_CLIENT_ID);
        lenient().when(config.getClientSecret()).thenReturn(SAMPLE_CLIENT_SECRET);
        lenient().when(config.isProduction()).thenReturn(false);
    }

    // ========================================================================
    // STEP 1: Validate the Token API Enhancement
    // POST /v1/tokens/bearer with x-include-refresh-token-hard-expires-in: true
    // ========================================================================

    @Nested
    @DisplayName("Step 1: Token API Enhancement – x-include-refresh-token-hard-expires-in")
    class Step1_TokenApiEnhancement {

        @Test
        @DisplayName("1.1 Request includes x-include-refresh-token-hard-expires-in header set to true")
        void requestIncludesHardExpiresHeader() throws Exception {
            // Arrange – mock the token endpoint response
            long hardExpiresIn = 157_000_000L; // ~4.97 years remaining
            Map<String, Object> responseMap = buildTokenResponse(hardExpiresIn);
            String responseJson = new ObjectMapper().writeValueAsString(responseMap);

            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(responseJson, HttpStatus.OK));
            when(objectMapper.readValue(eq(responseJson), eq(Map.class))).thenReturn(responseMap);

            // Act
            service.refreshTokenWithHardExpiration(SAMPLE_REFRESH_TOKEN);

            // Assert – capture the HttpEntity and verify the header
            @SuppressWarnings("unchecked")
            ArgumentCaptor<HttpEntity<?>> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
            verify(restTemplate).postForEntity(anyString(), entityCaptor.capture(), eq(String.class));

            HttpHeaders sentHeaders = entityCaptor.getValue().getHeaders();
            assertEquals("true",
                    sentHeaders.getFirst(RefreshTokenHardExpirationService.HARD_EXPIRES_REQUEST_HEADER),
                    "Header x-include-refresh-token-hard-expires-in must be 'true'");
        }

        @Test
        @DisplayName("1.2 Response body contains x_refresh_token_hard_expires_in field")
        void responseContainsHardExpiresField() throws Exception {
            long hardExpiresIn = 100_000_000L;
            Map<String, Object> responseMap = buildTokenResponse(hardExpiresIn);
            String responseJson = new ObjectMapper().writeValueAsString(responseMap);

            when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(String.class)))
                    .thenReturn(new ResponseEntity<>(responseJson, HttpStatus.OK));
            when(objectMapper.readValue(eq(responseJson), eq(Map.class))).thenReturn(responseMap);

            // Act
            Map<String, Object> result = service.refreshTokenWithHardExpiration(SAMPLE_REFRESH_TOKEN);

            // Assert
            assertTrue(result.containsKey("x_refresh_token_hard_expires_in"),
                    "Response must contain x_refresh_token_hard_expires_in");
            assertEquals(hardExpiresIn, ((Number) result.get("x_refresh_token_hard_expires_in")).longValue());
        }

        @Test
        @DisplayName("1.3 Hard expiration value is in seconds and within 5-year max")
        void hardExpirationValueIsInSecondsWithinMax() {
            Map<String, Object> response = buildTokenResponse(FIVE_YEARS_SECONDS);
            assertTrue(service.validateHardExpirationField(response),
                    "Max 5-year value should be valid");

            long almostExpired = 60L; // 1 minute left
            Map<String, Object> nearExpiry = buildTokenResponse(almostExpired);
            assertTrue(service.validateHardExpirationField(nearExpiry),
                    "Small positive value should be valid");
        }

        @Test
        @DisplayName("1.4 Validation rejects values exceeding 5-year max")
        void rejectsValueExceedingMax() {
            Map<String, Object> response = buildTokenResponse(FIVE_YEARS_SECONDS + 1);
            assertFalse(service.validateHardExpirationField(response),
                    "Value exceeding 5-year max should be invalid");
        }

        @Test
        @DisplayName("1.5 Validation rejects zero and negative values")
        void rejectsZeroAndNegativeValues() {
            assertFalse(service.validateHardExpirationField(buildTokenResponse(0)),
                    "Zero should be invalid");
            assertFalse(service.validateHardExpirationField(buildTokenResponse(-1)),
                    "Negative should be invalid");
        }

        @Test
        @DisplayName("1.6 Validation handles missing field gracefully")
        void handlesMissingField() {
            Map<String, Object> response = new HashMap<>();
            response.put("access_token", "token");
            // no x_refresh_token_hard_expires_in
            assertFalse(service.validateHardExpirationField(response));
            assertEquals(-1, service.getHardExpirationSeconds(response));
        }

        @Test
        @DisplayName("1.7 Null refresh token throws IllegalArgumentException")
        void nullRefreshTokenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.refreshTokenWithHardExpiration(null));
        }

        @Test
        @DisplayName("1.8 Blank refresh token throws IllegalArgumentException")
        void blankRefreshTokenThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.refreshTokenWithHardExpiration("   "));
        }

        @Test
        @DisplayName("1.9 getHardExpirationSeconds returns correct value when present")
        void getHardExpirationSecondsReturnsCorrectValue() {
            long expected = 86_400L; // 1 day
            Map<String, Object> response = buildTokenResponse(expected);
            assertEquals(expected, service.getHardExpirationSeconds(response));
        }
    }

    // ========================================================================
    // STEP 2: Test Developer Logic (Calculated Expiry)
    // Legacy fallback: compare old vs new refresh token expiry dates
    // ========================================================================

    @Nested
    @DisplayName("Step 2: Developer Logic – Calculated Expiry (Legacy Fallback)")
    class Step2_CalculatedExpiry {

        @Test
        @DisplayName("2.1 isHardExpired returns true when old and new expiry are identical")
        void identicalExpiriesIndicateHardExpiration() {
            long expiryEpoch = Instant.now().plusSeconds(2_592_000).getEpochSecond(); // 30 days out
            assertTrue(service.isHardExpired(expiryEpoch, expiryEpoch),
                    "Identical expiries mean the token hit the 5-year ceiling");
        }

        @Test
        @DisplayName("2.2 isHardExpired returns false when new expiry is later than old")
        void differentExpiriesNotHardExpired() {
            long oldExpiry = Instant.now().plusSeconds(2_592_000).getEpochSecond();
            long newExpiry = Instant.now().plusSeconds(15_552_000).getEpochSecond(); // extended by ~180 days
            assertFalse(service.isHardExpired(oldExpiry, newExpiry),
                    "Different expiries mean the token was successfully renewed");
        }

        @Test
        @DisplayName("2.3 isWithin30DayWindow detects proximity threshold")
        void within30DayWindowDetection() {
            long thirtyDaysInSeconds = 30L * 24 * 60 * 60;
            assertTrue(service.isWithin30DayWindow(thirtyDaysInSeconds),
                    "Exactly 30 days should be within window");
            assertTrue(service.isWithin30DayWindow(1),
                    "1 second remaining should be within window");
            assertFalse(service.isWithin30DayWindow(thirtyDaysInSeconds + 1),
                    "30 days + 1 second should be outside window");
            assertFalse(service.isWithin30DayWindow(0),
                    "Zero should not be within window (already expired)");
            assertFalse(service.isWithin30DayWindow(-1),
                    "Negative should not be within window");
        }

        @Test
        @DisplayName("2.4 requiresReAuth returns true when within 30d AND expiries match")
        void requiresReAuthWhenWithin30DaysAndExpiriesMatch() {
            long currentExpiresIn = 20L * 24 * 60 * 60; // 20 days
            long expiryEpoch = Instant.now().plusSeconds(currentExpiresIn).getEpochSecond();
            assertTrue(service.requiresReAuth(currentExpiresIn, expiryEpoch, expiryEpoch),
                    "Should require re-auth: within 30d window AND same expiry");
        }

        @Test
        @DisplayName("2.5 requiresReAuth returns false when outside 30-day window")
        void noReAuthWhenOutside30DayWindow() {
            long currentExpiresIn = 100L * 24 * 60 * 60; // 100 days out
            long expiryEpoch = Instant.now().plusSeconds(currentExpiresIn).getEpochSecond();
            assertFalse(service.requiresReAuth(currentExpiresIn, expiryEpoch, expiryEpoch),
                    "Should not require re-auth: outside 30-day window even if expiries match");
        }

        @Test
        @DisplayName("2.6 requiresReAuth returns false when within 30d but expiries differ")
        void noReAuthWhenExpiriesDiffer() {
            long currentExpiresIn = 10L * 24 * 60 * 60; // 10 days
            long oldExpiry = Instant.now().plusSeconds(currentExpiresIn).getEpochSecond();
            long newExpiry = oldExpiry + (180L * 24 * 60 * 60); // renewed 180 days
            assertFalse(service.requiresReAuth(currentExpiresIn, oldExpiry, newExpiry),
                    "Should not require re-auth: within 30d but token was successfully renewed");
        }

        @Test
        @DisplayName("2.7 estimateTokenCreationDate returns plausible instant")
        void estimateTokenCreationDatePlausible() {
            long halfLife = FIVE_YEARS_SECONDS / 2;
            Instant estimated = service.estimateTokenCreationDate(halfLife);
            // Should be approximately 2.5 years ago
            long daysDiff = java.time.Duration.between(estimated, Instant.now()).toDays();
            assertTrue(daysDiff > 900 && daysDiff < 920,
                    "Estimated creation ~2.5 years ago (" + daysDiff + " days)");
        }
    }

    // ========================================================================
    // STEP 3: Verify the Reconnect URL Integration
    // Reconnect CTA must go through Intuit proxy to prevent URL injection
    // ========================================================================

    @Nested
    @DisplayName("Step 3: Reconnect URL Integration – Intuit Proxy Validation")
    class Step3_ReconnectUrl {

        @Test
        @DisplayName("3.1 buildReconnectUrl creates correct proxy URL")
        void buildReconnectUrlCorrectFormat() {
            String appId = "ABcSKl3ng5RTgH";
            String realmId = "123456789";
            String url = service.buildReconnectUrl(appId, realmId);

            assertEquals(
                    "https://appcenter.intuit.com/app/connect/oauth2/request?appId=ABcSKl3ng5RTgH&realmId=123456789&mode=reconnect",
                    url);
        }

        @Test
        @DisplayName("3.2 Valid reconnect URL passes security check")
        void validReconnectUrlPassesSecurity() {
            String validUrl = "https://appcenter.intuit.com/app/connect/oauth2/request?appId=APP123&realmId=REALM456&mode=reconnect";
            assertTrue(service.isValidReconnectUrl(validUrl),
                    "URL through Intuit proxy should pass validation");
        }

        @Test
        @DisplayName("3.3 Malicious URL (non-Intuit domain) is rejected")
        void maliciousNonIntuitDomainRejected() {
            String maliciousUrl = "https://evil.com/app/connect/oauth2/request?appId=APP&realmId=REALM&mode=reconnect";
            assertFalse(service.isValidReconnectUrl(maliciousUrl),
                    "Non-Intuit domain must be rejected to prevent URL injection");
        }

        @Test
        @DisplayName("3.4 URL without mode=reconnect is rejected")
        void urlWithoutReconnectModeRejected() {
            String noMode = "https://appcenter.intuit.com/app/connect/oauth2/request?appId=APP&realmId=REALM";
            assertFalse(service.isValidReconnectUrl(noMode),
                    "URL without mode=reconnect must be rejected");
        }

        @Test
        @DisplayName("3.5 HTTP (non-HTTPS) reconnect URL is rejected")
        void httpUrlRejected() {
            String httpUrl = "http://appcenter.intuit.com/app/connect/oauth2/request?appId=APP&realmId=REALM&mode=reconnect";
            assertFalse(service.isValidReconnectUrl(httpUrl),
                    "HTTP (non-HTTPS) URL must be rejected");
        }

        @Test
        @DisplayName("3.6 Null and blank reconnect URLs are rejected")
        void nullAndBlankRejected() {
            assertFalse(service.isValidReconnectUrl(null));
            assertFalse(service.isValidReconnectUrl(""));
            assertFalse(service.isValidReconnectUrl("   "));
        }

        @Test
        @DisplayName("3.7 buildReconnectUrl rejects null/blank appId")
        void buildReconnectUrlRejectsNullAppId() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.buildReconnectUrl(null, "realm"));
            assertThrows(IllegalArgumentException.class,
                    () -> service.buildReconnectUrl("  ", "realm"));
        }

        @Test
        @DisplayName("3.8 buildReconnectUrl rejects null/blank realmId")
        void buildReconnectUrlRejectsNullRealmId() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.buildReconnectUrl("app", null));
            assertThrows(IllegalArgumentException.class,
                    () -> service.buildReconnectUrl("app", "  "));
        }

        @Test
        @DisplayName("3.9 URL with subdomain injection is rejected")
        void subdomainInjectionRejected() {
            String injected = "https://evil.appcenter.intuit.com/app/connect/oauth2/request?appId=APP&realmId=REALM&mode=reconnect";
            assertFalse(service.isValidReconnectUrl(injected),
                    "Subdomain injection must be rejected");
        }

        @Test
        @DisplayName("3.10 Built reconnect URL itself passes validation")
        void builtUrlPassesValidation() {
            String url = service.buildReconnectUrl("APP_ID_123", "REALM_456");
            assertTrue(service.isValidReconnectUrl(url),
                    "URL built by buildReconnectUrl() should always pass isValidReconnectUrl()");
        }
    }

    // ========================================================================
    // STEP 4: Notification Timeline Check
    // T-30d IPN → T-7d Email → Expiration Day Error
    // ========================================================================

    @Nested
    @DisplayName("Step 4: Notification Timeline – IPN, Email, Expiration")
    class Step4_NotificationTimeline {

        @Test
        @DisplayName("4.1 T-minus 30 days: IPN App Card Warning/Task appears")
        void thirtyDayWarningNotification() {
            long twentyNineDays = 29L * 24 * 60 * 60;
            assertEquals(NotificationLevel.WARNING_30_DAY,
                    service.getNotificationLevel(twentyNineDays),
                    "29 days remaining should trigger WARNING_30_DAY");
            assertTrue(service.shouldShowInProductNotification(twentyNineDays),
                    "IPN should appear at 29 days");
            assertFalse(service.shouldSendEmailNotification(twentyNineDays),
                    "Email should NOT be sent at 29 days (only at 7-day threshold)");
        }

        @Test
        @DisplayName("4.2 Exactly 30 days: boundary triggers IPN")
        void exactlyThirtyDays() {
            long thirtyDays = 30L * 24 * 60 * 60;
            assertEquals(NotificationLevel.WARNING_30_DAY,
                    service.getNotificationLevel(thirtyDays));
            assertTrue(service.shouldShowInProductNotification(thirtyDays));
        }

        @Test
        @DisplayName("4.3 T-minus 7 days: email sent to Primary Admin and connecting user")
        void sevenDayEmailNotification() {
            long sixDays = 6L * 24 * 60 * 60;
            assertEquals(NotificationLevel.CRITICAL_7_DAY,
                    service.getNotificationLevel(sixDays),
                    "6 days remaining should trigger CRITICAL_7_DAY");
            assertTrue(service.shouldSendEmailNotification(sixDays),
                    "Email notification should be sent at 6 days");
            assertTrue(service.shouldShowInProductNotification(sixDays),
                    "IPN should also still be visible at 6 days");
        }

        @Test
        @DisplayName("4.4 Exactly 7 days: boundary triggers email")
        void exactlySevenDays() {
            long sevenDays = 7L * 24 * 60 * 60;
            assertEquals(NotificationLevel.CRITICAL_7_DAY,
                    service.getNotificationLevel(sevenDays));
            assertTrue(service.shouldSendEmailNotification(sevenDays));
        }

        @Test
        @DisplayName("4.5 Expiration day (0 seconds): App Card Error, data sync stopped")
        void expirationDaySyncStopped() {
            assertEquals(NotificationLevel.EXPIRED,
                    service.getNotificationLevel(0),
                    "0 seconds should be EXPIRED");
            assertTrue(service.shouldStopDataSync(0),
                    "Data sync must stop on expiration day");
        }

        @Test
        @DisplayName("4.6 Negative seconds (past expiration): still EXPIRED")
        void pastExpirationStillExpired() {
            assertEquals(NotificationLevel.EXPIRED,
                    service.getNotificationLevel(-86400));
            assertTrue(service.shouldStopDataSync(-86400));
        }

        @Test
        @DisplayName("4.7 More than 30 days remaining: no notification needed")
        void noNotificationWhenFarFromExpiry() {
            long sixMonths = 180L * 24 * 60 * 60;
            assertEquals(NotificationLevel.NONE,
                    service.getNotificationLevel(sixMonths));
            assertFalse(service.shouldShowInProductNotification(sixMonths));
            assertFalse(service.shouldSendEmailNotification(sixMonths));
            assertFalse(service.shouldStopDataSync(sixMonths));
        }

        @Test
        @DisplayName("4.8 Full timeline progression: NONE → WARNING → CRITICAL → EXPIRED")
        void fullTimelineProgression() {
            // > 30 days
            assertEquals(NotificationLevel.NONE,
                    service.getNotificationLevel(31L * 24 * 60 * 60));
            // 30 days
            assertEquals(NotificationLevel.WARNING_30_DAY,
                    service.getNotificationLevel(30L * 24 * 60 * 60));
            // 15 days (between 30 and 7)
            assertEquals(NotificationLevel.WARNING_30_DAY,
                    service.getNotificationLevel(15L * 24 * 60 * 60));
            // 7 days
            assertEquals(NotificationLevel.CRITICAL_7_DAY,
                    service.getNotificationLevel(7L * 24 * 60 * 60));
            // 1 day
            assertEquals(NotificationLevel.CRITICAL_7_DAY,
                    service.getNotificationLevel(1L * 24 * 60 * 60));
            // 0 = expired
            assertEquals(NotificationLevel.EXPIRED,
                    service.getNotificationLevel(0));
        }

        @Test
        @DisplayName("4.9 Data sync should NOT stop while token is still valid")
        void dataSyncContinuesWhileValid() {
            assertFalse(service.shouldStopDataSync(1),
                    "1 second remaining: sync should still be running");
            assertFalse(service.shouldStopDataSync(FIVE_YEARS_SECONDS),
                    "Full 5 years remaining: sync should still be running");
        }
    }

    // ========================================================================
    // Helpers
    // ========================================================================

    /**
     * Builds a simulated token endpoint response map with standard fields
     * and the x_refresh_token_hard_expires_in field.
     */
    private Map<String, Object> buildTokenResponse(long hardExpiresInSeconds) {
        Map<String, Object> response = new HashMap<>();
        response.put("access_token", "eyJlbmciOiJBMTI4Q0JD...");
        response.put("refresh_token", "AB11587698723yBfbK5gR...");
        response.put("token_type", "bearer");
        response.put("expires_in", 3600);
        response.put("x_refresh_token_expires_in", 8726400);
        response.put("x_refresh_token_hard_expires_in", hardExpiresInSeconds);
        return response;
    }
}
