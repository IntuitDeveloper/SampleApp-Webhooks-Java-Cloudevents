package com.intuit.developer.sampleapp.webhooks.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.intuit.developer.sampleapp.webhooks.config.QuickBooksConfig;
import com.intuit.developer.sampleapp.webhooks.controllers.WebhooksViewController;
import com.intuit.developer.sampleapp.webhooks.service.QuickBooksOAuthService;
import com.intuit.developer.sampleapp.webhooks.service.WebhookStorageService;
import com.intuit.ipp.data.WebhooksCloudEvents;

import jakarta.servlet.http.HttpSession;

/**
 * Unit tests for WebhooksViewController
 */
@ExtendWith(MockitoExtension.class)
public class WebhooksViewControllerTest {
    
    @Mock
    private QuickBooksConfig config;
    
    @Mock
    private QuickBooksOAuthService oauthService;
    
    @Mock
    private WebhookStorageService webhookStorageService;
    
    @Mock
    private Model model;
    
    @Mock
    private HttpSession session;
    
    @Mock
    private RedirectAttributes redirectAttributes;
    
    @InjectMocks
    private WebhooksViewController controller;
    
    @BeforeEach
    public void setUp() {
        // Default setup - can be overridden in individual tests
    }
    
    // ========== index() tests ==========
    
    @Test
    public void testIndex_Connected() {
        // Arrange
        when(session.getAttribute("realm_id")).thenReturn("123456");
        when(config.getEnvironment()).thenReturn("sandbox");
        
        // Act
        String viewName = controller.index(model, session);
        
        // Assert
        assertEquals("index", viewName);
        verify(model).addAttribute("connected", true);
        verify(model).addAttribute("realmId", "123456");
        verify(model).addAttribute("environment", "sandbox");
    }
    
    @Test
    public void testIndex_NotConnected() {
        // Arrange
        when(session.getAttribute("realm_id")).thenReturn(null);
        
        // Act
        String viewName = controller.index(model, session);
        
        // Assert
        assertEquals("index", viewName);
        verify(model).addAttribute("connected", false);
        verify(model, never()).addAttribute(eq("realmId"), any());
        verify(model, never()).addAttribute(eq("environment"), any());
    }
    
    @Test
    public void testIndex_EmptyRealmId() {
        // Arrange
        when(session.getAttribute("realm_id")).thenReturn("");
        
        // Act
        String viewName = controller.index(model, session);
        
        // Assert
        assertEquals("index", viewName);
        verify(model).addAttribute("connected", false);
    }
    
    // ========== connectToQuickBooks() tests ==========
    
    @Test
    public void testConnectToQuickBooks_Success() throws Exception {
        // Arrange
        String authUrl = "https://appcenter.intuit.com/connect/oauth2";
        when(oauthService.getAuthorizationUrl(true)).thenReturn(authUrl);
        
        // Act
        String result = controller.connectToQuickBooks();
        
        // Assert
        assertEquals("redirect:" + authUrl, result);
        verify(oauthService).getAuthorizationUrl(true);
    }
    
    @Test
    public void testConnectToQuickBooks_Error() throws Exception {
        // Arrange
        when(oauthService.getAuthorizationUrl(anyBoolean()))
            .thenThrow(new RuntimeException("OAuth error"));
        
        // Act
        String result = controller.connectToQuickBooks();
        
        // Assert
        assertTrue(result.startsWith("redirect:/?error="));
        assertTrue(result.contains("OAuth error"));
    }
    
    // ========== oauthCallback() tests ==========
    
    @Test
    public void testOauthCallback_Success() throws Exception {
        // Arrange
        String code = "auth-code-123";
        String realmId = "realm-456";
        Map<String, Object> tokens = new HashMap<>();
        tokens.put("access_token", "access-token");
        tokens.put("refresh_token", "refresh-token");
        tokens.put("expires_in", 3600);
        
        when(oauthService.exchangeCodeForToken(code, realmId)).thenReturn(tokens);
        when(session.getId()).thenReturn("session-123");
        
        // Act
        String result = controller.oauthCallback(code, realmId, null, session, redirectAttributes);
        
        // Assert
        assertEquals("redirect:/dashboard", result);
        verify(session).setAttribute("access_token", "access-token");
        verify(session).setAttribute("refresh_token", "refresh-token");
        verify(session).setAttribute("realm_id", realmId);
        verify(session).setAttribute("expires_in", 3600);
        verify(redirectAttributes).addFlashAttribute("error", false);
        verify(redirectAttributes).addFlashAttribute(eq("message"), contains("Successfully connected"));
    }
    
    @Test
    public void testOauthCallback_WithError() {
        // Act
        String result = controller.oauthCallback(null, null, "access_denied", session, redirectAttributes);
        
        // Assert
        assertEquals("redirect:/", result);
        verify(redirectAttributes).addFlashAttribute("error", true);
        verify(redirectAttributes).addFlashAttribute(eq("message"), contains("access_denied"));
    }
    
    @Test
    public void testOauthCallback_MissingCode() {
        // Act
        String result = controller.oauthCallback(null, "realm-123", null, session, redirectAttributes);
        
        // Assert
        assertEquals("redirect:/", result);
        verify(redirectAttributes).addFlashAttribute("error", true);
        verify(redirectAttributes).addFlashAttribute(eq("message"), contains("Missing authorization code"));
    }
    
    @Test
    public void testOauthCallback_MissingRealmId() {
        // Act
        String result = controller.oauthCallback("code-123", null, null, session, redirectAttributes);
        
        // Assert
        assertEquals("redirect:/", result);
        verify(redirectAttributes).addFlashAttribute("error", true);
        verify(redirectAttributes).addFlashAttribute(eq("message"), contains("Missing authorization code"));
    }
    
    @Test
    public void testOauthCallback_ExchangeTokenError() throws Exception {
        // Arrange
        String code = "auth-code-123";
        String realmId = "realm-456";
        when(oauthService.exchangeCodeForToken(code, realmId))
            .thenThrow(new RuntimeException("Token exchange failed"));
        
        // Act
        String result = controller.oauthCallback(code, realmId, null, session, redirectAttributes);
        
        // Assert
        assertEquals("redirect:/", result);
        verify(session).removeAttribute("access_token");
        verify(session).removeAttribute("refresh_token");
        verify(session).removeAttribute("realm_id");
        verify(session).removeAttribute("expires_in");
        verify(redirectAttributes).addFlashAttribute("error", true);
        verify(redirectAttributes).addFlashAttribute(eq("message"), contains("Failed to connect"));
    }
    
    // ========== disconnectFromQuickBooks() tests ==========
    
    @Test
    public void testDisconnectFromQuickBooks_Success() throws Exception {
        // Arrange
        String refreshToken = "refresh-token-123";
        when(session.getAttribute("refresh_token")).thenReturn(refreshToken);
        
        // Act
        String result = controller.disconnectFromQuickBooks(session, redirectAttributes);
        
        // Assert
        assertEquals("redirect:/", result);
        verify(oauthService).revokeToken(refreshToken);
        verify(session).invalidate();
        verify(redirectAttributes).addFlashAttribute("error", false);
        verify(redirectAttributes).addFlashAttribute(eq("message"), contains("Successfully disconnected"));
    }
    
    @Test
    public void testDisconnectFromQuickBooks_NoRefreshToken() throws Exception {
        // Arrange
        when(session.getAttribute("refresh_token")).thenReturn(null);
        
        // Act
        String result = controller.disconnectFromQuickBooks(session, redirectAttributes);
        
        // Assert
        assertEquals("redirect:/", result);
        verify(oauthService, never()).revokeToken(any());
        verify(session).invalidate();
        verify(redirectAttributes).addFlashAttribute("error", false);
    }
    
    @Test
    public void testDisconnectFromQuickBooks_RevokeError() throws Exception {
        // Arrange
        String refreshToken = "refresh-token-123";
        when(session.getAttribute("refresh_token")).thenReturn(refreshToken);
        doThrow(new RuntimeException("Revoke failed")).when(oauthService).revokeToken(refreshToken);
        
        // Act
        String result = controller.disconnectFromQuickBooks(session, redirectAttributes);
        
        // Assert
        assertEquals("redirect:/", result);
        verify(redirectAttributes).addFlashAttribute("error", true);
        verify(redirectAttributes).addFlashAttribute(eq("message"), contains("Error disconnecting"));
    }
    
    // ========== dashboard() tests ==========
    
    @Test
    public void testDashboard_Success() {
        // Arrange
        String realmId = "realm-123";
        when(session.getAttribute("realm_id")).thenReturn(realmId);
        when(config.getEnvironment()).thenReturn("sandbox");
        when(webhookStorageService.getTotalEventCount()).thenReturn(5);
        
        List<WebhooksCloudEvents> mockEvents = new ArrayList<>();
        WebhooksCloudEvents event = mock(WebhooksCloudEvents.class);
        mockEvents.add(event);
        when(webhookStorageService.getRecentCloudEvents()).thenReturn(mockEvents);
        
        // Act
        String result = controller.dashboard(model, session);
        
        // Assert
        assertEquals("dashboard", result);
        verify(model).addAttribute("realmId", realmId);
        verify(model).addAttribute("environment", "sandbox");
        verify(model).addAttribute("webhookCount", 5);
        verify(model).addAttribute("todayCount", 5);
        verify(model).addAttribute("cloudEvents", mockEvents);
    }
    
    @Test
    public void testDashboard_NoRealmId() {
        // Arrange
        when(session.getAttribute("realm_id")).thenReturn(null);
        when(session.getId()).thenReturn("session-123");
        
        // Act
        String result = controller.dashboard(model, session);
        
        // Assert
        assertTrue(result.startsWith("redirect:/"));
        assertTrue(result.contains("error=Not connected"));
        verify(model, never()).addAttribute(eq("realmId"), any());
    }
    
    @Test
    public void testDashboard_EmptyRealmId() {
        // Arrange
        when(session.getAttribute("realm_id")).thenReturn("");
        
        // Act
        String result = controller.dashboard(model, session);
        
        // Assert
        assertTrue(result.startsWith("redirect:/"));
        assertTrue(result.contains("error=Not connected"));
    }
    
    @Test
    public void testDashboard_NoWebhooks() {
        // Arrange
        when(session.getAttribute("realm_id")).thenReturn("realm-123");
        when(config.getEnvironment()).thenReturn("production");
        when(webhookStorageService.getTotalEventCount()).thenReturn(0);
        when(webhookStorageService.getRecentCloudEvents()).thenReturn(new ArrayList<>());
        
        // Act
        String result = controller.dashboard(model, session);
        
        // Assert
        assertEquals("dashboard", result);
        verify(model).addAttribute("webhookCount", 0);
        verify(model).addAttribute(eq("cloudEvents"), anyList());
    }
    
    @Test
    public void testDashboard_MultipleWebhooks() {
        // Arrange
        when(session.getAttribute("realm_id")).thenReturn("realm-123");
        when(config.getEnvironment()).thenReturn("sandbox");
        when(webhookStorageService.getTotalEventCount()).thenReturn(25);
        
        List<WebhooksCloudEvents> mockEvents = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            mockEvents.add(mock(WebhooksCloudEvents.class));
        }
        when(webhookStorageService.getRecentCloudEvents()).thenReturn(mockEvents);
        
        // Act
        String result = controller.dashboard(model, session);
        
        // Assert
        assertEquals("dashboard", result);
        verify(model).addAttribute("webhookCount", 25);
        verify(model).addAttribute("cloudEvents", mockEvents);
    }
}
