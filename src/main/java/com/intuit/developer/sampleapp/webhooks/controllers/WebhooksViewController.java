package com.intuit.developer.sampleapp.webhooks.controllers;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.intuit.developer.sampleapp.webhooks.config.QuickBooksConfig;
import com.intuit.developer.sampleapp.webhooks.service.QuickBooksOAuthService;
import com.intuit.developer.sampleapp.webhooks.service.WebhookStorageService;
import com.intuit.ipp.core.Context;
import com.intuit.ipp.core.ServiceType;
import com.intuit.ipp.data.Bill;
import com.intuit.ipp.data.Customer;
import com.intuit.ipp.data.Invoice;
import com.intuit.ipp.data.JournalEntry;
import com.intuit.ipp.data.Payment;
import com.intuit.ipp.data.Purchase;
import com.intuit.ipp.data.Vendor;
import com.intuit.ipp.security.OAuth2Authorizer;
import com.intuit.ipp.services.DataService;
import com.intuit.ipp.services.QueryResult;

import jakarta.servlet.http.HttpSession;

/**
 * Web UI Controller for Webhooks Dashboard
 * 
 * Handles:
 * - Home page display
 * - OAuth connect/callback/disconnect flow
 * - Webhooks dashboard display
 * - Session management for OAuth tokens
 * 
 * Based on modern MVC patterns from SampleApp-Dimensions-Java
 */
@Controller
public class WebhooksViewController {
    
    private static final Logger logger = LoggerFactory.getLogger(WebhooksViewController.class);
    
    @Autowired
    private QuickBooksConfig config;
    
    @Autowired
    private QuickBooksOAuthService oauthService;
    
    @Autowired
    private WebhookStorageService webhookStorageService;
    
    
    /**
     * Home page
     */
    @GetMapping("/")
    public String index(Model model, HttpSession session) {
        String realmId = (String) session.getAttribute("realm_id");
        boolean connected = realmId != null && !realmId.isEmpty();
        
        model.addAttribute("connected", connected);
        
        if (connected) {
            model.addAttribute("realmId", realmId);
            model.addAttribute("environment", config.getEnvironment());
        }
        
        return "index";
    }
    
    /**
     * Initiate OAuth connection
     */
    @GetMapping("/oauth/connect")
    public String connectToQuickBooks() {
        try {
            String authUrl = oauthService.getAuthorizationUrl(true);
            logger.info("Redirecting to QuickBooks OAuth URL");
            return "redirect:" + authUrl;
        } catch (Exception e) {
            logger.error("Error initiating OAuth connection: {}", e.getMessage());
            return "redirect:/?error=" + e.getMessage();
        }
    }
    
    /**
     * OAuth callback handler
     */
    @GetMapping("/callback")
    public String oauthCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String realmId,
            @RequestParam(required = false) String error,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        
        logger.debug("OAuth callback - Session ID: {}", session.getId());
        
        if (error != null) {
            logger.error("OAuth error: {}", error);
            redirectAttributes.addFlashAttribute("error", true);
            redirectAttributes.addFlashAttribute("message", "OAuth authorization failed: " + error);
            return "redirect:/";
        }
        
        if (code == null || realmId == null) {
            logger.error("Missing authorization code or realm ID");
            redirectAttributes.addFlashAttribute("error", true);
            redirectAttributes.addFlashAttribute("message", "Missing authorization code or realm ID");
            return "redirect:/";
        }
        
        try {
            // Exchange code for tokens
            Map<String, Object> tokens = oauthService.exchangeCodeForToken(code, realmId);
            
            // Store tokens in session
            session.setAttribute("access_token", tokens.get("access_token"));
            session.setAttribute("refresh_token", tokens.get("refresh_token"));
            session.setAttribute("realm_id", realmId);
            session.setAttribute("expires_in", tokens.get("expires_in"));
            
            logger.info("Session attributes set - Session ID: {}, Realm ID: {}", session.getId(), realmId);
            logger.info("Successfully connected to QuickBooks. Realm ID: {} (tokens stored in session)", realmId);
            
            redirectAttributes.addFlashAttribute("error", false);
            redirectAttributes.addFlashAttribute("message", "Successfully connected to QuickBooks!");
            
            return "redirect:/dashboard";
            
        } catch (Exception e) {
            logger.error("Error during OAuth callback: {}", e.getMessage(), e);
            
            // Clear any partial session data
            session.removeAttribute("access_token");
            session.removeAttribute("refresh_token");
            session.removeAttribute("realm_id");
            session.removeAttribute("expires_in");
            
            redirectAttributes.addFlashAttribute("error", true);
            redirectAttributes.addFlashAttribute("message", "Failed to connect to QuickBooks: " + e.getMessage());
            return "redirect:/";
        }
    }
    
    /**
     * Disconnect from QuickBooks
     */
    @GetMapping("/oauth/disconnect")
    public String disconnectFromQuickBooks(HttpSession session, RedirectAttributes redirectAttributes) {
        try {
            String refreshToken = (String) session.getAttribute("refresh_token");
            
            if (refreshToken != null) {
                oauthService.revokeToken(refreshToken);
            }
            
            // Clear session
            session.invalidate();
            logger.info("Successfully disconnected from QuickBooks");
            
            redirectAttributes.addFlashAttribute("error", false);
            redirectAttributes.addFlashAttribute("message", "Successfully disconnected from QuickBooks");
            
        } catch (Exception e) {
            logger.error("Error during disconnect: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", true);
            redirectAttributes.addFlashAttribute("message", "Error disconnecting: " + e.getMessage());
        }
        
        return "redirect:/";
    }
    
    /**
     * Webhooks dashboard
     */
    @GetMapping("/dashboard")
    public String dashboard(Model model, HttpSession session) {
        logger.debug("Dashboard accessed - Session ID: {}", session.getId());
        
        String realmId = (String) session.getAttribute("realm_id");
        logger.debug("Dashboard - Realm ID from session: {}", realmId);
        
        if (realmId == null || realmId.isEmpty()) {
            logger.warn("Dashboard access denied - no realm_id in session. Session ID: {}", session.getId());
            return "redirect:/?error=Not connected to QuickBooks";
        }
        
        // Add connection info
        model.addAttribute("realmId", realmId);
        model.addAttribute("environment", config.getEnvironment());
        
        // Add CloudEvents webhook stats
        int totalEvents = webhookStorageService.getTotalEventCount();
        model.addAttribute("webhookCount", totalEvents);
        model.addAttribute("todayCount", totalEvents); // Simplified for demo
        
        // Add event type breakdown for stats display
        java.util.Map<String, Integer> eventTypeBreakdown = webhookStorageService.getEventTypeBreakdown();
        model.addAttribute("eventTypeBreakdown", eventTypeBreakdown);
        
        // Add recent webhooks for dashboard display
        java.util.List<com.intuit.developer.sampleapp.webhooks.domain.WebhookEvent> recentWebhooks = 
            webhookStorageService.getRecentWebhooks();
        logger.info("Dashboard - Retrieved {} webhooks from storage", recentWebhooks.size());
        model.addAttribute("webhookEvents", recentWebhooks);
        
        return "dashboard";
    }
    
    /**
     * Event details page - shows detailed information about a specific webhook event
     */
    @GetMapping("/webhooks/details/{index}")
    public String eventDetails(@PathVariable int index, Model model, HttpSession session) {
        logger.debug("Event details accessed for index: {}", index);
        
        String realmId = (String) session.getAttribute("realm_id");
        if (realmId == null || realmId.isEmpty()) {
            logger.warn("Event details access denied - no realm_id in session");
            return "redirect:/?error=Not connected to QuickBooks";
        }
        
        // Get the specific event by index
        com.intuit.developer.sampleapp.webhooks.domain.WebhookEvent webhookEvent = webhookStorageService.getEventByIndex(index);
        
        if (webhookEvent == null) {
            logger.warn("Event not found for index: {}", index);
            return "redirect:/dashboard?error=Event not found";
        }
        
        // Add event data to model
        model.addAttribute("webhookEvent", webhookEvent);
        model.addAttribute("eventIndex", index);
        model.addAttribute("realmId", realmId);
        model.addAttribute("environment", config.getEnvironment());
        
        // If this is a customer event, fetch current customer data
        if ("Customer".equalsIgnoreCase(webhookEvent.getEntityName())) {
            try {
                Customer customer = fetchCustomerData(webhookEvent.getEntityId(), session);
                if (customer != null) {
                    model.addAttribute("customerData", customer);
                    logger.info("Fetched current customer data for ID: {}", webhookEvent.getEntityId());
                }
            } catch (Exception e) {
                logger.warn("Could not fetch customer data: {}", e.getMessage());
                model.addAttribute("customerDataError", "Unable to fetch current customer data");
            }
        }
        
        // If this is a vendor event, fetch current vendor data
        if ("Vendor".equalsIgnoreCase(webhookEvent.getEntityName())) {
            try {
                Vendor vendor = fetchVendorData(webhookEvent.getEntityId(), session);
                if (vendor != null) {
                    model.addAttribute("vendorData", vendor);
                    logger.info("Fetched current vendor data for ID: {}", webhookEvent.getEntityId());
                }
            } catch (Exception e) {
                logger.warn("Could not fetch vendor data: {}", e.getMessage());
                model.addAttribute("vendorDataError", "Unable to fetch current vendor data");
            }
        }
        
        // If this is an invoice event, fetch current invoice data
        if ("Invoice".equalsIgnoreCase(webhookEvent.getEntityName())) {
            try {
                Invoice invoice = fetchInvoiceData(webhookEvent.getEntityId(), session);
                if (invoice != null) {
                    model.addAttribute("invoiceData", invoice);
                    logger.info("Fetched current invoice data for ID: {}", webhookEvent.getEntityId());
                }
            } catch (Exception e) {
                logger.warn("Could not fetch invoice data: {}", e.getMessage());
                model.addAttribute("invoiceDataError", "Unable to fetch current invoice data");
            }
        }
        
        // If this is a payment event, fetch current payment data
        if ("Payment".equalsIgnoreCase(webhookEvent.getEntityName())) {
            try {
                Payment payment = fetchPaymentData(webhookEvent.getEntityId(), session);
                if (payment != null) {
                    model.addAttribute("paymentData", payment);
                    logger.info("Fetched current payment data for ID: {}", webhookEvent.getEntityId());
                }
            } catch (Exception e) {
                logger.warn("Could not fetch payment data: {}", e.getMessage());
                model.addAttribute("paymentDataError", "Unable to fetch current payment data");
            }
        }
        
        // If this is a bill event, fetch current bill data
        if ("Bill".equalsIgnoreCase(webhookEvent.getEntityName())) {
            try {
                Bill bill = fetchBillData(webhookEvent.getEntityId(), session);
                if (bill != null) {
                    model.addAttribute("billData", bill);
                    logger.info("Fetched current bill data for ID: {}", webhookEvent.getEntityId());
                }
            } catch (Exception e) {
                logger.warn("Could not fetch bill data: {}", e.getMessage());
                model.addAttribute("billDataError", "Unable to fetch current bill data");
            }
        }
        
        // If this is a journal entry event, fetch current journal entry data
        if ("JournalEntry".equalsIgnoreCase(webhookEvent.getEntityName())) {
            try {
                JournalEntry journalEntry = fetchJournalEntryData(webhookEvent.getEntityId(), session);
                if (journalEntry != null) {
                    model.addAttribute("journalEntryData", journalEntry);
                    logger.info("Fetched current journal entry data for ID: {}", webhookEvent.getEntityId());
                }
            } catch (Exception e) {
                logger.warn("Could not fetch journal entry data: {}", e.getMessage());
                model.addAttribute("journalEntryDataError", "Unable to fetch current journal entry data");
            }
        }
        
        // If this is a purchase event, fetch current purchase data
        if ("Purchase".equalsIgnoreCase(webhookEvent.getEntityName())) {
            try {
                Purchase purchase = fetchPurchaseData(webhookEvent.getEntityId(), session);
                if (purchase != null) {
                    model.addAttribute("purchaseData", purchase);
                    logger.info("Fetched current purchase data for ID: {}", webhookEvent.getEntityId());
                }
            } catch (Exception e) {
                logger.warn("Could not fetch purchase data: {}", e.getMessage());
                model.addAttribute("purchaseDataError", "Unable to fetch current purchase data");
            }
        }
        
        logger.info("Displaying event details for: entity={}, id={}, operation={}", 
            webhookEvent.getEntityName(), webhookEvent.getEntityId(), webhookEvent.getOperation());
        
        return "event-details";
    }
    
    /**
     * Fetch customer data from QuickBooks API
     */
    private Customer fetchCustomerData(String customerId, HttpSession session) throws Exception {
        String accessToken = (String) session.getAttribute("access_token");
        String realmId = (String) session.getAttribute("realm_id");
        
        if (accessToken == null || realmId == null) {
            throw new RuntimeException("Not authenticated");
        }
        
        OAuth2Authorizer oauth2Authorizer = new OAuth2Authorizer(accessToken);
        Context context = new Context(oauth2Authorizer, ServiceType.QBO, realmId);
        DataService dataService = new DataService(context);
        
        String query = "SELECT * FROM Customer WHERE Id = '" + customerId + "'";
        QueryResult queryResult = dataService.executeQuery(query);
        
        if (queryResult.getEntities() != null && !queryResult.getEntities().isEmpty()) {
            return (Customer) queryResult.getEntities().get(0);
        }
        
        return null;
    }
    
    /**
     * Fetch vendor data from QuickBooks API
     */
    private Vendor fetchVendorData(String vendorId, HttpSession session) throws Exception {
        String accessToken = (String) session.getAttribute("access_token");
        String realmId = (String) session.getAttribute("realm_id");
        
        if (accessToken == null || realmId == null) {
            throw new RuntimeException("Not authenticated");
        }
        
        OAuth2Authorizer oauth2Authorizer = new OAuth2Authorizer(accessToken);
        Context context = new Context(oauth2Authorizer, ServiceType.QBO, realmId);
        DataService dataService = new DataService(context);
        
        String query = "SELECT * FROM Vendor WHERE Id = '" + vendorId + "'";
        QueryResult queryResult = dataService.executeQuery(query);
        
        if (queryResult.getEntities() != null && !queryResult.getEntities().isEmpty()) {
            return (Vendor) queryResult.getEntities().get(0);
        }
        
        return null;
    }
    
    /**
     * Fetch invoice data from QuickBooks API
     */
    private Invoice fetchInvoiceData(String invoiceId, HttpSession session) throws Exception {
        String accessToken = (String) session.getAttribute("access_token");
        String realmId = (String) session.getAttribute("realm_id");
        
        if (accessToken == null || realmId == null) {
            throw new RuntimeException("Not authenticated");
        }
        
        OAuth2Authorizer oauth2Authorizer = new OAuth2Authorizer(accessToken);
        Context context = new Context(oauth2Authorizer, ServiceType.QBO, realmId);
        DataService dataService = new DataService(context);
        
        String query = "SELECT * FROM Invoice WHERE Id = '" + invoiceId + "'";
        QueryResult queryResult = dataService.executeQuery(query);
        
        if (queryResult.getEntities() != null && !queryResult.getEntities().isEmpty()) {
            return (Invoice) queryResult.getEntities().get(0);
        }
        
        return null;
    }
    
    /**
     * Fetch payment data from QuickBooks API
     */
    private Payment fetchPaymentData(String paymentId, HttpSession session) throws Exception {
        String accessToken = (String) session.getAttribute("access_token");
        String realmId = (String) session.getAttribute("realm_id");
        
        if (accessToken == null || realmId == null) {
            throw new RuntimeException("Not authenticated");
        }
        
        OAuth2Authorizer oauth2Authorizer = new OAuth2Authorizer(accessToken);
        Context context = new Context(oauth2Authorizer, ServiceType.QBO, realmId);
        DataService dataService = new DataService(context);
        
        String query = "SELECT * FROM Payment WHERE Id = '" + paymentId + "'";
        QueryResult queryResult = dataService.executeQuery(query);
        
        if (queryResult.getEntities() != null && !queryResult.getEntities().isEmpty()) {
            return (Payment) queryResult.getEntities().get(0);
        }
        
        return null;
    }
    
    /**
     * Fetch bill data from QuickBooks API
     */
    private Bill fetchBillData(String billId, HttpSession session) throws Exception {
        String accessToken = (String) session.getAttribute("access_token");
        String realmId = (String) session.getAttribute("realm_id");
        
        if (accessToken == null || realmId == null) {
            throw new RuntimeException("Not authenticated");
        }
        
        OAuth2Authorizer oauth2Authorizer = new OAuth2Authorizer(accessToken);
        Context context = new Context(oauth2Authorizer, ServiceType.QBO, realmId);
        DataService dataService = new DataService(context);
        
        String query = "SELECT * FROM Bill WHERE Id = '" + billId + "'";
        QueryResult queryResult = dataService.executeQuery(query);
        
        if (queryResult.getEntities() != null && !queryResult.getEntities().isEmpty()) {
            return (Bill) queryResult.getEntities().get(0);
        }
        
        return null;
    }
    
    /**
     * Fetch journal entry data from QuickBooks API
     */
    private JournalEntry fetchJournalEntryData(String journalEntryId, HttpSession session) throws Exception {
        String accessToken = (String) session.getAttribute("access_token");
        String realmId = (String) session.getAttribute("realm_id");
        
        if (accessToken == null || realmId == null) {
            throw new RuntimeException("Not authenticated");
        }
        
        OAuth2Authorizer oauth2Authorizer = new OAuth2Authorizer(accessToken);
        Context context = new Context(oauth2Authorizer, ServiceType.QBO, realmId);
        DataService dataService = new DataService(context);
        
        String query = "SELECT * FROM JournalEntry WHERE Id = '" + journalEntryId + "'";
        QueryResult queryResult = dataService.executeQuery(query);
        
        if (queryResult.getEntities() != null && !queryResult.getEntities().isEmpty()) {
            return (JournalEntry) queryResult.getEntities().get(0);
        }
        
        return null;
    }
    
    /**
     * Fetch purchase data from QuickBooks API
     */
    private Purchase fetchPurchaseData(String purchaseId, HttpSession session) throws Exception {
        String accessToken = (String) session.getAttribute("access_token");
        String realmId = (String) session.getAttribute("realm_id");
        
        if (accessToken == null || realmId == null) {
            throw new RuntimeException("Not authenticated");
        }
        
        OAuth2Authorizer oauth2Authorizer = new OAuth2Authorizer(accessToken);
        Context context = new Context(oauth2Authorizer, ServiceType.QBO, realmId);
        DataService dataService = new DataService(context);
        
        String query = "SELECT * FROM Purchase WHERE Id = '" + purchaseId + "'";
        QueryResult queryResult = dataService.executeQuery(query);
        
        if (queryResult.getEntities() != null && !queryResult.getEntities().isEmpty()) {
            return (Purchase) queryResult.getEntities().get(0);
        }
        
        return null;
    }
}
