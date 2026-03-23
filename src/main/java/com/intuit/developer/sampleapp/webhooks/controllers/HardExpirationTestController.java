package com.intuit.developer.sampleapp.webhooks.controllers;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.intuit.developer.sampleapp.webhooks.config.QuickBooksConfig;
import com.intuit.developer.sampleapp.webhooks.service.QuickBooksOAuthService;
import com.intuit.developer.sampleapp.webhooks.service.RefreshTokenHardExpirationService;
import com.intuit.developer.sampleapp.webhooks.service.RefreshTokenHardExpirationService.NotificationLevel;

import jakarta.servlet.http.HttpSession;

/**
 * Test harness controller for the Refresh Token Hard Expiration feature.
 *
 * <p>Provides a visual UI to execute and validate all 4 steps of the
 * Hard Expiration QA prompt against a live Intuit OAuth 2.0 environment.</p>
 *
 * <p>Flow: Home → OAuth Connect → Test Harness page with 4 step panels.</p>
 */
@Controller
public class HardExpirationTestController {

    private static final Logger logger = LoggerFactory.getLogger(HardExpirationTestController.class);
    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());

    @Autowired
    private QuickBooksOAuthService oauthService;

    @Autowired
    private RefreshTokenHardExpirationService hardExpirationService;

    @Autowired
    private QuickBooksConfig config;

    // ==================== OAuth Flow ====================

    /** Home page — shows Connect button or link to test harness */
    @GetMapping("/")
    public String home(HttpSession session, Model model) {
        boolean connected = session.getAttribute("refresh_token") != null;
        model.addAttribute("connected", connected);
        if (connected) {
            model.addAttribute("realmId", session.getAttribute("realm_id"));
            model.addAttribute("environment", config.getEnvironment());
        }
        return "index";
    }

    /** Initiates OAuth 2.0 authorization flow */
    @GetMapping("/oauth/connect")
    public String connect() {
        String authUrl = oauthService.getAuthorizationUrl();
        logger.info("Redirecting to OAuth authorization URL");
        return "redirect:" + authUrl;
    }

    /** OAuth callback — exchanges auth code for tokens, stores in session */
    @GetMapping("/oauth/callback")
    public String callback(@RequestParam("code") String code,
                           @RequestParam("realmId") String realmId,
                           HttpSession session,
                           RedirectAttributes redirectAttributes) {
        try {
            Map<String, Object> tokens = oauthService.exchangeCodeForToken(code, realmId);
            session.setAttribute("access_token", tokens.get("access_token"));
            session.setAttribute("refresh_token", tokens.get("refresh_token"));
            session.setAttribute("realm_id", realmId);
            // Store initial token timestamp for expiry calculations
            session.setAttribute("token_obtained_at", Instant.now().getEpochSecond());
            redirectAttributes.addFlashAttribute("message", "Connected to QuickBooks! Company: " + realmId);
            logger.info("OAuth callback success — realm {}", realmId);
            return "redirect:/test-harness";
        } catch (Exception e) {
            logger.error("OAuth callback failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("message", "Connection failed: " + e.getMessage());
            redirectAttributes.addFlashAttribute("error", true);
            return "redirect:/";
        }
    }

    /** Disconnect — revoke token and clear session */
    @GetMapping("/oauth/disconnect")
    public String disconnect(HttpSession session, RedirectAttributes redirectAttributes) {
        String refreshToken = (String) session.getAttribute("refresh_token");
        oauthService.revokeToken(refreshToken);
        session.invalidate();
        redirectAttributes.addFlashAttribute("message", "Disconnected from QuickBooks.");
        return "redirect:/";
    }

    // ==================== Test Harness Page ====================

    /** Renders the test harness with current session/token state */
    @GetMapping("/test-harness")
    public String testHarness(HttpSession session, Model model) {
        boolean connected = session.getAttribute("refresh_token") != null;
        model.addAttribute("connected", connected);
        model.addAttribute("realmId", session.getAttribute("realm_id"));
        model.addAttribute("environment", config.getEnvironment());
        model.addAttribute("clientId", maskValue(config.getClientId()));

        // If we already ran Step 1, show cached results
        if (session.getAttribute("step1_result") != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> step1 = (Map<String, Object>) session.getAttribute("step1_result");
            populateStep1Model(model, step1);
        }

        // Step 2 results
        if (session.getAttribute("step2_result") != null) {
            @SuppressWarnings("unchecked")
            Map<String, Object> step2 = (Map<String, Object>) session.getAttribute("step2_result");
            model.addAllAttributes(step2);
        }

        // Step 3 — always compute
        populateStep3Model(model, (String) session.getAttribute("realm_id"));

        // Step 4 — compute if we have hard-expiration seconds
        if (session.getAttribute("hard_expires_in") != null) {
            long hardExpiresIn = (long) session.getAttribute("hard_expires_in");
            populateStep4Model(model, hardExpiresIn);
        }

        return "hard-expiration-test";
    }

    // ==================== Step 1: Token API Enhancement ====================

    /** Executes a token refresh with the x-include-refresh-token-hard-expires-in header */
    @PostMapping("/test/step1")
    public String executeStep1(HttpSession session, RedirectAttributes redirectAttributes) {
        String refreshToken = (String) session.getAttribute("refresh_token");
        if (refreshToken == null) {
            redirectAttributes.addFlashAttribute("step1Error", "No refresh token in session. Connect first.");
            return "redirect:/test-harness";
        }

        try {
            Map<String, Object> result = hardExpirationService.refreshTokenWithHardExpiration(refreshToken);

            // Update session with new tokens
            if (result.get("access_token") != null) {
                session.setAttribute("access_token", result.get("access_token"));
            }
            if (result.get("refresh_token") != null) {
                session.setAttribute("refresh_token", result.get("refresh_token"));
            }

            // Store hard-expiration value if present
            if (result.containsKey("x_refresh_token_hard_expires_in")) {
                long hardExpiresIn = ((Number) result.get("x_refresh_token_hard_expires_in")).longValue();
                session.setAttribute("hard_expires_in", hardExpiresIn);
            }

            // Cache the response for display
            session.setAttribute("step1_result", result);
            redirectAttributes.addFlashAttribute("step1Success", true);

        } catch (Exception e) {
            logger.error("Step 1 failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("step1Error", "Token refresh failed: " + e.getMessage());
        }
        return "redirect:/test-harness";
    }

    // ==================== Step 2: Calculated Expiry Test ====================

    /** Executes the legacy fallback: refresh again and compare old vs new expiry */
    @PostMapping("/test/step2")
    public String executeStep2(HttpSession session, RedirectAttributes redirectAttributes) {
        String refreshToken = (String) session.getAttribute("refresh_token");
        if (refreshToken == null) {
            redirectAttributes.addFlashAttribute("step2Error", "No refresh token. Connect first.");
            return "redirect:/test-harness";
        }

        try {
            // First refresh — capture the current x_refresh_token_expires_in
            Map<String, Object> firstRefresh = hardExpirationService.refreshTokenWithHardExpiration(refreshToken);
            long oldExpiresIn = firstRefresh.containsKey("x_refresh_token_expires_in")
                    ? ((Number) firstRefresh.get("x_refresh_token_expires_in")).longValue()
                    : 0;
            long oldExpiryEpoch = Instant.now().plusSeconds(oldExpiresIn).getEpochSecond();

            // Update the refresh token (may have rotated)
            String newRefreshToken = firstRefresh.get("refresh_token") != null
                    ? (String) firstRefresh.get("refresh_token")
                    : refreshToken;
            session.setAttribute("refresh_token", newRefreshToken);

            // Second refresh — compare expiry
            Map<String, Object> secondRefresh = hardExpirationService.refreshTokenWithHardExpiration(newRefreshToken);
            long newExpiresIn = secondRefresh.containsKey("x_refresh_token_expires_in")
                    ? ((Number) secondRefresh.get("x_refresh_token_expires_in")).longValue()
                    : 0;
            long newExpiryEpoch = Instant.now().plusSeconds(newExpiresIn).getEpochSecond();

            // Update session tokens
            if (secondRefresh.get("access_token") != null) {
                session.setAttribute("access_token", secondRefresh.get("access_token"));
            }
            if (secondRefresh.get("refresh_token") != null) {
                session.setAttribute("refresh_token", secondRefresh.get("refresh_token"));
            }

            // Build result
            boolean isHardExpired = hardExpirationService.isHardExpired(oldExpiryEpoch, newExpiryEpoch);
            boolean within30d = hardExpirationService.isWithin30DayWindow(oldExpiresIn);
            boolean requiresReAuth = hardExpirationService.requiresReAuth(oldExpiresIn, oldExpiryEpoch, newExpiryEpoch);

            Map<String, Object> step2Result = new HashMap<>();
            step2Result.put("step2_oldExpiresIn", oldExpiresIn);
            step2Result.put("step2_newExpiresIn", newExpiresIn);
            step2Result.put("step2_oldExpiryDate", FORMATTER.format(Instant.ofEpochSecond(oldExpiryEpoch)));
            step2Result.put("step2_newExpiryDate", FORMATTER.format(Instant.ofEpochSecond(newExpiryEpoch)));
            step2Result.put("step2_expiriesMatch", isHardExpired);
            step2Result.put("step2_within30d", within30d);
            step2Result.put("step2_requiresReAuth", requiresReAuth);

            session.setAttribute("step2_result", step2Result);
            redirectAttributes.addFlashAttribute("step2Success", true);

        } catch (Exception e) {
            logger.error("Step 2 failed: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("step2Error", "Calculated expiry test failed: " + e.getMessage());
        }
        return "redirect:/test-harness";
    }

    // ==================== Step 3: Reconnect URL (JSON endpoint) ====================

    /** Returns reconnect URL validation result as JSON */
    @GetMapping("/test/step3/validate")
    @ResponseBody
    public Map<String, Object> validateReconnectUrl(@RequestParam("url") String url) {
        Map<String, Object> result = new HashMap<>();
        result.put("url", url);
        result.put("valid", hardExpirationService.isValidReconnectUrl(url));
        return result;
    }

    // ==================== Helpers ====================

    private void populateStep1Model(Model model, Map<String, Object> tokenResponse) {
        model.addAttribute("step1_response", tokenResponse);
        model.addAttribute("step1_maskedAccessToken", truncateToken(tokenResponse.get("access_token")));
        model.addAttribute("step1_maskedRefreshToken", truncateToken(tokenResponse.get("refresh_token")));
        boolean hasHardExpires = tokenResponse.containsKey("x_refresh_token_hard_expires_in");
        model.addAttribute("step1_hasHardExpires", hasHardExpires);
        if (hasHardExpires) {
            long hardExpiresIn = ((Number) tokenResponse.get("x_refresh_token_hard_expires_in")).longValue();
            model.addAttribute("step1_hardExpiresIn", hardExpiresIn);
            model.addAttribute("step1_hardExpiresDays", hardExpiresIn / 86400);
            model.addAttribute("step1_valid", hardExpirationService.validateHardExpirationField(tokenResponse));
            model.addAttribute("step1_expiryDate",
                    FORMATTER.format(Instant.now().plusSeconds(hardExpiresIn)));
        }
    }

    private void populateStep3Model(Model model, String realmId) {
        String appId = config.getClientId();
        if (appId != null && realmId != null) {
            String reconnectUrl = hardExpirationService.buildReconnectUrl(appId, realmId);
            model.addAttribute("step3_reconnectUrl", reconnectUrl);
            model.addAttribute("step3_reconnectUrlValid", hardExpirationService.isValidReconnectUrl(reconnectUrl));
        }
        // Also check configured reconnect URL
        String configuredUrl = config.getReconnectUrl();
        model.addAttribute("step3_configuredUrl", configuredUrl);
        model.addAttribute("step3_configuredUrlValid",
                configuredUrl != null && hardExpirationService.isValidReconnectUrl(configuredUrl));
    }

    private void populateStep4Model(Model model, long hardExpiresIn) {
        NotificationLevel level = hardExpirationService.getNotificationLevel(hardExpiresIn);
        model.addAttribute("step4_level", level.name());
        model.addAttribute("step4_showIPN", hardExpirationService.shouldShowInProductNotification(hardExpiresIn));
        model.addAttribute("step4_sendEmail", hardExpirationService.shouldSendEmailNotification(hardExpiresIn));
        model.addAttribute("step4_stopSync", hardExpirationService.shouldStopDataSync(hardExpiresIn));
        model.addAttribute("step4_hardExpiresIn", hardExpiresIn);
        model.addAttribute("step4_hardExpiresDays", hardExpiresIn / 86400);
        model.addAttribute("step4_expiryDate", FORMATTER.format(Instant.now().plusSeconds(hardExpiresIn)));

        // Simulation values for the timeline demo
        model.addAttribute("step4_sim30d", hardExpirationService.getNotificationLevel(
                30L * 24 * 60 * 60).name());
        model.addAttribute("step4_sim7d", hardExpirationService.getNotificationLevel(
                7L * 24 * 60 * 60).name());
        model.addAttribute("step4_sim0d", hardExpirationService.getNotificationLevel(0).name());
    }

    private String truncateToken(Object value) {
        if (value == null) return null;
        String str = value.toString();
        if (str.length() <= 20) return str + "...";
        return str.substring(0, 20) + "...";
    }

    private String maskValue(String value) {
        if (value == null || value.length() < 4) return "***";
        return value.substring(0, 4) + "***" + value.substring(value.length() - 2);
    }
}
