package com.intuit.developer.sampleapp.webhooks.service;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.intuit.ipp.data.WebhooksCloudEvents;

/**
 * Parser for CloudEvents format webhooks
 * 
 * CloudEvents structure:
 * [{
 *   "specversion": "1.0",
 *   "id": "event-id",
 *   "type": "qbo.customer.created.v1",
 *   "time": "2025-12-02T10:00:00Z",
 *   "intuitentityid": "123",
 *   "intuitaccountid": "987654",
 *   "data": {}
 * }]
 */
@Service
public class CloudEventsWebhookParser {
    
    private static final Logger LOG = LoggerFactory.getLogger(CloudEventsWebhookParser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    
    /**
     * Check if payload is CloudEvents format
     */
    public boolean isCloudEventsFormat(String payload) {
        if (payload == null || payload.trim().isEmpty()) {
            return false;
        }
        
        String trimmed = payload.trim();
        // CloudEvents format is a JSON array starting with [
        if (trimmed.startsWith("[")) {
            try {
                JsonElement element = JsonParser.parseString(trimmed);
                if (element.isJsonArray()) {
                    JsonArray array = element.getAsJsonArray();
                    if (array.size() > 0) {
                        JsonObject first = array.get(0).getAsJsonObject();
                        // Check for CloudEvents required fields
                        return first.has("specversion") && first.has("type");
                    }
                }
            } catch (JsonSyntaxException | IllegalStateException e) {
                LOG.debug("Invalid CloudEvents format: {}", e.getMessage());
                return false;
            }
        }
        return false;
    }
    
    /**
     * Parse CloudEvents payload using SDK's WebhooksCloudEvents
     * Returns SDK objects directly - pure SDK usage
     */
    public List<WebhooksCloudEvents> parseCloudEvents(String payload) {
        final List<WebhooksCloudEvents> events = new ArrayList<>();
        
        try {
            // Deserialize JSON into SDK's WebhooksCloudEvents objects using Jackson
            List<WebhooksCloudEvents> cloudEvents = objectMapper.readValue(
                payload, 
                new TypeReference<List<WebhooksCloudEvents>>() {}
            );
            
            LOG.info("Parsing {} CloudEvents webhook(s)", cloudEvents.size());
            
            for (WebhooksCloudEvents sdkEvent : cloudEvents) {
                try {
                    events.add(sdkEvent);
                    
                    LOG.info("Parsed CloudEvent: id={}, type={}, entityId={}, accountId={}", 
                        sdkEvent.getId(),
                        sdkEvent.getType(),
                        sdkEvent.getIntuitEntityId(), 
                        sdkEvent.getIntuitAccountId());
                        
                } catch (Exception e) {
                    LOG.error("Failed to parse individual CloudEvent: {}", e.getMessage(), e);
                }
            }
            
            LOG.info("Successfully parsed {} CloudEvent(s)", events.size());
            
        } catch (JsonProcessingException e) {
            LOG.error("Failed to parse CloudEvents payload: {}", e.getMessage(), e);
        }
        
        return events;
    }
    
}
