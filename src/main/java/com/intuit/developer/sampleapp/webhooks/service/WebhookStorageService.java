package com.intuit.developer.sampleapp.webhooks.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.intuit.ipp.data.WebhooksCloudEvents;
import com.intuit.ipp.util.Logger;

/**
 * Service for storing and retrieving CloudEvents webhooks for dashboard display.
 * 
 * <p>This service provides in-memory storage of incoming CloudEvents from QuickBooks.
 * It maintains a thread-safe collection of recent webhook events with a configurable
 * maximum capacity. When the limit is reached, the oldest events are automatically removed.</p>
 * 
 * <p><strong>Important:</strong> This implementation uses in-memory storage for demo purposes.
 * In a production environment, webhooks should be persisted to a database for durability,
 * analytics, and audit trails.</p>
 * 
 * <p>Thread-safety: All public methods are synchronized to prevent concurrent modification
 * issues when webhooks are received from multiple threads.</p>
 * 
 * @author Nate O'Neal
 * @version 1.0
 * @since 2025-11-18
 */
@Service
public class WebhookStorageService {
    
    private static final org.slf4j.Logger LOG = Logger.getLogger();
    private static final int MAX_WEBHOOKS = 50; // Maximum number of webhooks to retain
    
    private final List<WebhooksCloudEvents> recentCloudEvents = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> processedEventIds = Collections.synchronizedSet(new HashSet<>());
    
    @Autowired
    private CloudEventsWebhookParser cloudEventsParser;
    
    /**
     * Adds a webhook event to storage for dashboard display.
     * 
     * <p>This method parses the webhook payload, extracts entity change events,
     * and stores them for display on the dashboard. Multiple entities within a
     * single webhook notification are stored as separate events.</p>
     * 
     * <p>The storage maintains a FIFO queue with a maximum capacity of {@value MAX_WEBHOOKS}
     * events. When the limit is reached, the oldest event is automatically removed.</p>
     * 
     * <p>This method is thread-safe and can be called concurrently from multiple threads.</p>
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
            // Process as CloudEvents format
            LOG.info("Processing CloudEvents format webhook");
            processCloudEventsWebhook(payload);
            
        } catch (IllegalArgumentException e) {
            // Re-throw validation exceptions
            throw e;
        } catch (Exception e) {
            LOG.error("Failed to parse and store webhook: {}", e.getMessage(), e);
            throw new RuntimeException("Error processing webhook payload", e);
        }
    }
    
    /**
     * Process CloudEvents format webhook payload using SDK
     * Stores pure SDK WebhooksCloudEvents objects
     */
    private void processCloudEventsWebhook(String payload) {
        List<WebhooksCloudEvents> events = cloudEventsParser.parseCloudEvents(payload);
        
        if (events.isEmpty()) {
            LOG.warn("CloudEvents webhook contained no processable events");
            return;
        }
        
        // Add all CloudEvents to storage
        for (WebhooksCloudEvents event : events) {
            String eventId = event.getId();

            if (eventId != null && processedEventIds.contains(eventId)) {
                LOG.info("Skipping duplicate CloudEvent: id={} (already processed)", eventId);
                continue;
            }

            if (eventId != null) {
                processedEventIds.add(eventId);
            }

            // Add to beginning of list (most recent first)
            recentCloudEvents.add(0, event);
            
            // Maintain maximum size limit
            if (recentCloudEvents.size() > MAX_WEBHOOKS) {
                recentCloudEvents.remove(recentCloudEvents.size() - 1);
                LOG.debug("Removed oldest CloudEvent to maintain size limit of {}", MAX_WEBHOOKS);
            }
            
            LOG.info("Stored CloudEvent: id={}, type={}, entityId={}, accountId={}", 
                event.getId(), event.getType(), event.getIntuitEntityId(), event.getIntuitAccountId());
        }
        
        LOG.info("Successfully processed CloudEvents webhook with {} event(s)", events.size());
    }
    
    /**
     * Clears all stored CloudEvents from memory.
     */
    public synchronized void clearWebhooks() {
        int previousSize = recentCloudEvents.size();
        recentCloudEvents.clear();
        processedEventIds.clear();
        LOG.info("Cleared {} CloudEvent(s) from storage", previousSize);
    }
    
    /**
     * Returns the maximum number of webhook events that can be stored.
     * 
     * @return Maximum webhook storage capacity
     */
    public int getMaxCapacity() {
        return MAX_WEBHOOKS;
    }
    
    /**
     * Retrieves all recent CloudEvents for dashboard display.
     * 
     * @return List of SDK WebhooksCloudEvents (most recent first)
     */
    public synchronized List<WebhooksCloudEvents> getRecentCloudEvents() {
        return Collections.unmodifiableList(new ArrayList<>(recentCloudEvents));
    }
    
    /**
     * Returns total count of stored CloudEvents 
     */
    public synchronized int getTotalEventCount() {
        return recentCloudEvents.size();
    }
    
    /**
     * Gets event type breakdown with counts for dashboard display.
     * Returns a map of event types to their counts, ordered by count (descending).
     * 
     * @return Map of event type to count
     */
    public synchronized Map<String, Integer> getEventTypeBreakdown() {
        Map<String, Integer> eventTypeCounts = new LinkedHashMap<>();
        
        for (WebhooksCloudEvents event : recentCloudEvents) {
            String eventType = event.getType();
            if (eventType != null) {
                eventTypeCounts.put(eventType, eventTypeCounts.getOrDefault(eventType, 0) + 1);
            }
        }
        
        return eventTypeCounts;
    }
    
    /**
     * Gets a specific CloudEvent by index for detailed view.
     * 
     * @param index The index of the event (0-based, most recent first)
     * @return The CloudEvent at the specified index, or null if index is out of bounds
     */
    public synchronized WebhooksCloudEvents getEventByIndex(int index) {
        if (index >= 0 && index < recentCloudEvents.size()) {
            return recentCloudEvents.get(index);
        }
        return null;
    }
}
