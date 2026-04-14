package com.intuit.developer.sampleapp.webhooks.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intuit.developer.sampleapp.webhooks.domain.WebhookEvent;
import com.intuit.ipp.util.Logger;

/**
 * Service for storing and retrieving webhook events for dashboard display.
 * 
 * <p>This service provides in-memory storage of incoming webhook notifications from QuickBooks.
 * It maintains a thread-safe collection of recent webhook events with a configurable
 * maximum capacity. When the limit is reached, the oldest events are automatically removed.</p>
 * 
 * <p><strong>Important:</strong> This implementation uses in-memory storage for demo purposes.
 * In a production environment, webhooks should be persisted to a database for durability,
 * analytics, and audit trails.</p>
 * 
 * @author Nate O'Neal
 * @version 1.0
 * @since 2025-11-18
 */
@Service
public class WebhookStorageService {
    
    private static final org.slf4j.Logger LOG = Logger.getLogger();
    private static final int MAX_WEBHOOKS = 50;
    
    private final List<WebhookEvent> recentWebhooks = Collections.synchronizedList(new ArrayList<>());
    
    /**
     * Adds a webhook event to storage for dashboard display.
     * Parses the legacy eventNotifications payload and extracts entity changes.
     * 
     * @param payload The raw webhook payload JSON string from QuickBooks
     * @throws IllegalArgumentException if payload is null or empty
     */
    public synchronized void addWebhook(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            LOG.warn("Attempted to store null or empty webhook payload");
            throw new IllegalArgumentException("Webhook payload cannot be null or empty");
        }
        
        try {
            JsonObject root = JsonParser.parseString(payload).getAsJsonObject();
            JsonArray notifications = root.getAsJsonArray("eventNotifications");
            
            if (notifications == null || notifications.isEmpty()) {
                LOG.warn("Webhook contained no event notifications");
                return;
            }
            
            for (JsonElement notifElement : notifications) {
                JsonObject notification = notifElement.getAsJsonObject();
                String realmId = notification.get("realmId").getAsString();
                
                JsonObject dataChangeEvent = notification.getAsJsonObject("dataChangeEvent");
                JsonArray entities = dataChangeEvent.getAsJsonArray("entities");
                
                for (JsonElement entityElement : entities) {
                    JsonObject entity = entityElement.getAsJsonObject();
                    
                    WebhookEvent event = new WebhookEvent(
                        realmId,
                        entity.get("name").getAsString(),
                        entity.get("id").getAsString(),
                        entity.get("operation").getAsString(),
                        entity.get("lastUpdated").getAsString()
                    );
                    
                    // Add to beginning of list (most recent first)
                    recentWebhooks.add(0, event);
                    
                    // Maintain maximum size limit
                    if (recentWebhooks.size() > MAX_WEBHOOKS) {
                        recentWebhooks.remove(recentWebhooks.size() - 1);
                    }
                    
                    LOG.info("Stored webhook event: realmId={}, entity={}, id={}, operation={}", 
                        realmId, event.getEntityName(), event.getEntityId(), event.getOperation());
                }
            }
            
            LOG.info("Successfully processed webhook with {} notification(s)", notifications.size());
            
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to parse and store webhook: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing webhook payload", e);
        }
    }
    
    /**
     * Clears all stored webhooks from memory.
     */
    public synchronized void clearWebhooks() {
        int previousSize = recentWebhooks.size();
        recentWebhooks.clear();
        LOG.info("Cleared {} webhook(s) from storage", previousSize);
    }
    
    /**
     * Returns the maximum number of webhook events that can be stored.
     */
    public int getMaxCapacity() {
        return MAX_WEBHOOKS;
    }
    
    /**
     * Retrieves all recent webhooks for dashboard display.
     * 
     * @return List of WebhookEvent objects (most recent first)
     */
    public synchronized List<WebhookEvent> getRecentWebhooks() {
        return Collections.unmodifiableList(new ArrayList<>(recentWebhooks));
    }
    
    /**
     * Returns total count of stored webhook events.
     */
    public synchronized int getTotalEventCount() {
        return recentWebhooks.size();
    }
    
    /**
     * Gets event type breakdown with counts for dashboard display.
     * 
     * @return Map of entity operation (e.g. "Customer.Create") to count
     */
    public synchronized Map<String, Integer> getEventTypeBreakdown() {
        Map<String, Integer> operationCounts = new LinkedHashMap<>();
        
        for (WebhookEvent event : recentWebhooks) {
            String key = event.getEntityName() + "." + event.getOperation();
            operationCounts.put(key, operationCounts.getOrDefault(key, 0) + 1);
        }
        
        return operationCounts;
    }
    
    /**
     * Gets a specific webhook event by index for detailed view.
     * 
     * @param index The index of the event (0-based, most recent first)
     * @return The WebhookEvent at the specified index, or null if index is out of bounds
     */
    public synchronized WebhookEvent getEventByIndex(int index) {
        if (index >= 0 && index < recentWebhooks.size()) {
            return recentWebhooks.get(index);
        }
        return null;
    }
}
