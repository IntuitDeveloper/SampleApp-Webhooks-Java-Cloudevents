package com.intuit.developer.sampleapp.webhooks.service;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.intuit.developer.sampleapp.webhooks.domain.WebhookEvent;

/**
 * Unit tests for WebhookStorageService (legacy webhook format)
 */
public class WebhookStorageServiceTest {
    
    private WebhookStorageService webhookStorageService;
    
    private static final String VALID_PAYLOAD = "{\"eventNotifications\":[{\"realmId\":\"9341455322581846\",\"dataChangeEvent\":{\"entities\":[{\"name\":\"Customer\",\"id\":\"42\",\"operation\":\"Create\",\"lastUpdated\":\"2025-04-10T11:00:00-07:00\"}]}}]}";
    
    private static final String MULTI_ENTITY_PAYLOAD = "{\"eventNotifications\":[{\"realmId\":\"9341455322581846\",\"dataChangeEvent\":{\"entities\":[{\"name\":\"Customer\",\"id\":\"42\",\"operation\":\"Create\",\"lastUpdated\":\"2025-04-10T11:00:00-07:00\"},{\"name\":\"Invoice\",\"id\":\"100\",\"operation\":\"Update\",\"lastUpdated\":\"2025-04-10T11:05:00-07:00\"},{\"name\":\"Vendor\",\"id\":\"7\",\"operation\":\"Delete\",\"lastUpdated\":\"2025-04-10T11:10:00-07:00\"}]}}]}";
    
    @BeforeEach
    public void setUp() {
        webhookStorageService = new WebhookStorageService();
    }
    
    @Test
    public void testAddWebhook_ValidPayload() {
        webhookStorageService.addWebhook(VALID_PAYLOAD);
        
        assertEquals(1, webhookStorageService.getTotalEventCount());
        List<WebhookEvent> events = webhookStorageService.getRecentWebhooks();
        assertEquals(1, events.size());
        assertEquals("9341455322581846", events.get(0).getRealmId());
        assertEquals("Customer", events.get(0).getEntityName());
        assertEquals("42", events.get(0).getEntityId());
        assertEquals("Create", events.get(0).getOperation());
    }
    
    @Test
    public void testAddWebhook_NullPayload() {
        assertThrows(IllegalArgumentException.class, () -> webhookStorageService.addWebhook(null));
    }
    
    @Test
    public void testAddWebhook_EmptyPayload() {
        assertThrows(IllegalArgumentException.class, () -> webhookStorageService.addWebhook(""));
    }
    
    @Test
    public void testAddWebhook_WhitespacePayload() {
        assertThrows(IllegalArgumentException.class, () -> webhookStorageService.addWebhook("   "));
    }
    
    @Test
    public void testAddWebhook_MultipleEntities() {
        webhookStorageService.addWebhook(MULTI_ENTITY_PAYLOAD);
        assertEquals(3, webhookStorageService.getTotalEventCount());
    }
    
    @Test
    public void testAddWebhook_EmptyNotifications() {
        webhookStorageService.addWebhook("{\"eventNotifications\":[]}");
        assertEquals(0, webhookStorageService.getTotalEventCount());
    }
    
    @Test
    public void testAddWebhook_ExceedsMaxCapacity() {
        int maxCapacity = webhookStorageService.getMaxCapacity();
        
        for (int i = 0; i < maxCapacity + 5; i++) {
            String payload = "{\"eventNotifications\":[{\"realmId\":\"realm-" + i + "\",\"dataChangeEvent\":{\"entities\":[{\"name\":\"Customer\",\"id\":\"" + i + "\",\"operation\":\"Create\",\"lastUpdated\":\"2025-04-10T11:00:00-07:00\"}]}}]}";
            webhookStorageService.addWebhook(payload);
        }
        
        assertEquals(maxCapacity, webhookStorageService.getTotalEventCount());
    }
    
    @Test
    public void testAddWebhook_InvalidJson() {
        assertThrows(RuntimeException.class, () -> webhookStorageService.addWebhook("invalid json"));
    }
    
    @Test
    public void testClearWebhooks() {
        webhookStorageService.addWebhook(VALID_PAYLOAD);
        assertEquals(1, webhookStorageService.getTotalEventCount());
        
        webhookStorageService.clearWebhooks();
        
        assertEquals(0, webhookStorageService.getTotalEventCount());
        assertTrue(webhookStorageService.getRecentWebhooks().isEmpty());
    }
    
    @Test
    public void testGetRecentWebhooks_ReturnsUnmodifiableList() {
        webhookStorageService.addWebhook(VALID_PAYLOAD);
        List<WebhookEvent> events = webhookStorageService.getRecentWebhooks();
        assertThrows(UnsupportedOperationException.class, () -> events.clear());
    }
    
    @Test
    public void testGetRecentWebhooks_MostRecentFirst() {
        for (int i = 0; i < 3; i++) {
            String payload = "{\"eventNotifications\":[{\"realmId\":\"realm-" + i + "\",\"dataChangeEvent\":{\"entities\":[{\"name\":\"Customer\",\"id\":\"" + i + "\",\"operation\":\"Create\",\"lastUpdated\":\"2025-04-10T11:00:00-07:00\"}]}}]}";
            webhookStorageService.addWebhook(payload);
        }
        
        List<WebhookEvent> events = webhookStorageService.getRecentWebhooks();
        assertEquals(3, events.size());
        assertEquals("2", events.get(0).getEntityId());
        assertEquals("0", events.get(2).getEntityId());
    }
    
    @Test
    public void testGetTotalEventCount_EmptyStorage() {
        assertEquals(0, webhookStorageService.getTotalEventCount());
    }
    
    @Test
    public void testGetMaxCapacity() {
        assertEquals(50, webhookStorageService.getMaxCapacity());
    }
    
    @Test
    public void testGetEventTypeBreakdown() {
        webhookStorageService.addWebhook(MULTI_ENTITY_PAYLOAD);
        
        java.util.Map<String, Integer> breakdown = webhookStorageService.getEventTypeBreakdown();
        assertNotNull(breakdown);
        assertEquals(1, breakdown.getOrDefault("Customer.Create", 0));
        assertEquals(1, breakdown.getOrDefault("Invoice.Update", 0));
        assertEquals(1, breakdown.getOrDefault("Vendor.Delete", 0));
    }
    
    @Test
    public void testGetEventByIndex() {
        webhookStorageService.addWebhook(VALID_PAYLOAD);
        
        WebhookEvent event = webhookStorageService.getEventByIndex(0);
        assertNotNull(event);
        assertEquals("Customer", event.getEntityName());
        
        assertNull(webhookStorageService.getEventByIndex(99));
        assertNull(webhookStorageService.getEventByIndex(-1));
    }
    
    @Test
    public void testConcurrentAccess() throws InterruptedException {
        Thread thread1 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                String payload = "{\"eventNotifications\":[{\"realmId\":\"realm-t1\",\"dataChangeEvent\":{\"entities\":[{\"name\":\"Customer\",\"id\":\"t1-" + i + "\",\"operation\":\"Create\",\"lastUpdated\":\"2025-04-10T11:00:00-07:00\"}]}}]}";
                webhookStorageService.addWebhook(payload);
            }
        });
        
        Thread thread2 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                String payload = "{\"eventNotifications\":[{\"realmId\":\"realm-t2\",\"dataChangeEvent\":{\"entities\":[{\"name\":\"Vendor\",\"id\":\"t2-" + i + "\",\"operation\":\"Update\",\"lastUpdated\":\"2025-04-10T11:00:00-07:00\"}]}}]}";
                webhookStorageService.addWebhook(payload);
            }
        });
        
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
        
        assertEquals(20, webhookStorageService.getTotalEventCount());
    }
}
