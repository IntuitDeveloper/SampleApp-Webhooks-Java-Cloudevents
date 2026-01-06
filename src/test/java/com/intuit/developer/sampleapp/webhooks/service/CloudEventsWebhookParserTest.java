package com.intuit.developer.sampleapp.webhooks.service;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.intuit.ipp.data.WebhooksCloudEvents;

/**
 * Unit tests for CloudEventsWebhookParser
 */
public class CloudEventsWebhookParserTest {
    
    private CloudEventsWebhookParser parser;
    
    @BeforeEach
    public void setUp() {
        parser = new CloudEventsWebhookParser();
    }
    
    @Test
    public void testIsCloudEventsFormat_ValidFormat() {
        // Arrange
        String validPayload = "[{\"specversion\":\"1.0\",\"type\":\"qbo.customer.created.v1\",\"id\":\"test-123\"}]";
        
        // Act & Assert
        assertTrue(parser.isCloudEventsFormat(validPayload));
    }
    
    @Test
    public void testIsCloudEventsFormat_MultipleEvents() {
        // Arrange
        String validPayload = "[" +
            "{\"specversion\":\"1.0\",\"type\":\"qbo.customer.created.v1\"}," +
            "{\"specversion\":\"1.0\",\"type\":\"qbo.invoice.created.v1\"}" +
            "]";
        
        // Act & Assert
        assertTrue(parser.isCloudEventsFormat(validPayload));
    }
    
    @Test
    public void testIsCloudEventsFormat_WithWhitespace() {
        // Arrange
        String validPayload = "  [{\"specversion\":\"1.0\",\"type\":\"qbo.customer.created.v1\"}]  ";
        
        // Act & Assert
        assertTrue(parser.isCloudEventsFormat(validPayload));
    }
    
    @Test
    public void testIsCloudEventsFormat_NullPayload() {
        // Act & Assert
        assertFalse(parser.isCloudEventsFormat(null));
    }
    
    @Test
    public void testIsCloudEventsFormat_EmptyPayload() {
        // Act & Assert
        assertFalse(parser.isCloudEventsFormat(""));
    }
    
    @Test
    public void testIsCloudEventsFormat_WhitespaceOnly() {
        // Act & Assert
        assertFalse(parser.isCloudEventsFormat("   "));
    }
    
    @Test
    public void testIsCloudEventsFormat_NotJsonArray() {
        // Arrange
        String invalidPayload = "{\"specversion\":\"1.0\",\"type\":\"qbo.customer.created.v1\"}";
        
        // Act & Assert
        assertFalse(parser.isCloudEventsFormat(invalidPayload));
    }
    
    @Test
    public void testIsCloudEventsFormat_EmptyArray() {
        // Act & Assert
        assertFalse(parser.isCloudEventsFormat("[]"));
    }
    
    @Test
    public void testIsCloudEventsFormat_MissingSpecVersion() {
        // Arrange
        String invalidPayload = "[{\"type\":\"qbo.customer.created.v1\",\"id\":\"test-123\"}]";
        
        // Act & Assert
        assertFalse(parser.isCloudEventsFormat(invalidPayload));
    }
    
    @Test
    public void testIsCloudEventsFormat_MissingType() {
        // Arrange
        String invalidPayload = "[{\"specversion\":\"1.0\",\"id\":\"test-123\"}]";
        
        // Act & Assert
        assertFalse(parser.isCloudEventsFormat(invalidPayload));
    }
    
    @Test
    public void testIsCloudEventsFormat_InvalidJson() {
        // Arrange
        String invalidPayload = "[{invalid json}]";
        
        // Act & Assert
        assertFalse(parser.isCloudEventsFormat(invalidPayload));
    }
    
    @Test
    public void testIsCloudEventsFormat_ArrayWithNonObject() {
        // Arrange
        String invalidPayload = "[\"string\"]";
        
        // Act & Assert
        assertFalse(parser.isCloudEventsFormat(invalidPayload));
    }
    
    @Test
    public void testParseCloudEvents_ValidSingleEvent() {
        // Arrange
        String payload = "[{" +
            "\"specversion\":\"1.0\"," +
            "\"id\":\"event-123\"," +
            "\"type\":\"qbo.customer.created.v1\"," +
            "\"time\":\"2025-12-03T10:00:00Z\"," +
            "\"intuitentityid\":\"456\"," +
            "\"intuitaccountid\":\"789\"" +
            "}]";
        
        // Act
        List<WebhooksCloudEvents> events = parser.parseCloudEvents(payload);
        
        // Assert
        assertNotNull(events);
        assertEquals(1, events.size());
        WebhooksCloudEvents event = events.get(0);
        assertEquals("event-123", event.getId());
        assertEquals("qbo.customer.created.v1", event.getType());
        assertEquals("456", event.getIntuitEntityId());
        assertEquals("789", event.getIntuitAccountId());
    }
    
    @Test
    public void testParseCloudEvents_MultipleEvents() {
        // Arrange
        String payload = "[" +
            "{\"specversion\":\"1.0\",\"id\":\"event-1\",\"type\":\"qbo.customer.created.v1\"}," +
            "{\"specversion\":\"1.0\",\"id\":\"event-2\",\"type\":\"qbo.invoice.created.v1\"}," +
            "{\"specversion\":\"1.0\",\"id\":\"event-3\",\"type\":\"qbo.payment.created.v1\"}" +
            "]";
        
        // Act
        List<WebhooksCloudEvents> events = parser.parseCloudEvents(payload);
        
        // Assert
        assertNotNull(events);
        assertEquals(3, events.size());
        assertEquals("event-1", events.get(0).getId());
        assertEquals("event-2", events.get(1).getId());
        assertEquals("event-3", events.get(2).getId());
    }
    
    @Test
    public void testParseCloudEvents_EmptyArray() {
        // Arrange
        String payload = "[]";
        
        // Act
        List<WebhooksCloudEvents> events = parser.parseCloudEvents(payload);
        
        // Assert
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }
    
    @Test
    public void testParseCloudEvents_InvalidJson() {
        // Arrange
        String invalidPayload = "invalid json";
        
        // Act
        List<WebhooksCloudEvents> events = parser.parseCloudEvents(invalidPayload);
        
        // Assert
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }
    
    @Test
    public void testParseCloudEvents_MalformedJson() {
        // Arrange
        String malformedPayload = "[{incomplete";
        
        // Act
        List<WebhooksCloudEvents> events = parser.parseCloudEvents(malformedPayload);
        
        // Assert
        assertNotNull(events);
        assertTrue(events.isEmpty());
    }
    
    @Test
    public void testParseCloudEvents_WithAllFields() {
        // Arrange
        String payload = "[{" +
            "\"specversion\":\"1.0\"," +
            "\"id\":\"complete-event\"," +
            "\"source\":\"intuit.source\"," +
            "\"type\":\"qbo.customer.created.v1\"," +
            "\"datacontenttype\":\"application/json\"," +
            "\"time\":\"2025-12-03T10:00:00Z\"," +
            "\"intuitentityid\":\"100\"," +
            "\"intuitaccountid\":\"200\"," +
            "\"data\":{}"+
            "}]";
        
        // Act
        List<WebhooksCloudEvents> events = parser.parseCloudEvents(payload);
        
        // Assert
        assertNotNull(events);
        assertEquals(1, events.size());
        WebhooksCloudEvents event = events.get(0);
        assertEquals("complete-event", event.getId());
        assertEquals("qbo.customer.created.v1", event.getType());
        assertEquals("100", event.getIntuitEntityId());
        assertEquals("200", event.getIntuitAccountId());
    }
    
    @Test
    public void testParseCloudEvents_DeletedEvent() {
        // Arrange
        String payload = "[{" +
            "\"specversion\":\"1.0\"," +
            "\"id\":\"delete-event\"," +
            "\"type\":\"qbo.invoice.deleted.v1\"," +
            "\"intuitentityid\":\"29\"," +
            "\"intuitaccountid\":\"9341455322581846\"" +
            "}]";
        
        // Act
        List<WebhooksCloudEvents> events = parser.parseCloudEvents(payload);
        
        // Assert
        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals("qbo.invoice.deleted.v1", events.get(0).getType());
    }
    
    @Test
    public void testParseCloudEvents_UpdatedEvent() {
        // Arrange
        String payload = "[{" +
            "\"specversion\":\"1.0\"," +
            "\"id\":\"update-event\"," +
            "\"type\":\"qbo.customer.updated.v1\"," +
            "\"intuitentityid\":\"42\"," +
            "\"intuitaccountid\":\"123456\"" +
            "}]";
        
        // Act
        List<WebhooksCloudEvents> events = parser.parseCloudEvents(payload);
        
        // Assert
        assertNotNull(events);
        assertEquals(1, events.size());
        assertEquals("qbo.customer.updated.v1", events.get(0).getType());
    }
    
    @Test
    public void testParseCloudEvents_DifferentEntityTypes() {
        // Arrange
        String payload = "[" +
            "{\"specversion\":\"1.0\",\"id\":\"e1\",\"type\":\"qbo.customer.created.v1\"}," +
            "{\"specversion\":\"1.0\",\"id\":\"e2\",\"type\":\"qbo.vendor.created.v1\"}," +
            "{\"specversion\":\"1.0\",\"id\":\"e3\",\"type\":\"qbo.invoice.created.v1\"}," +
            "{\"specversion\":\"1.0\",\"id\":\"e4\",\"type\":\"qbo.payment.created.v1\"}" +
            "]";
        
        // Act
        List<WebhooksCloudEvents> events = parser.parseCloudEvents(payload);
        
        // Assert
        assertEquals(4, events.size());
        assertTrue(events.get(0).getType().contains("customer"));
        assertTrue(events.get(1).getType().contains("vendor"));
        assertTrue(events.get(2).getType().contains("invoice"));
        assertTrue(events.get(3).getType().contains("payment"));
    }
}
