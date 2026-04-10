package com.intuit.developer.sampleapp.webhooks.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.intuit.ipp.data.WebhooksCloudEvents;

/**
 * Unit tests for WebhookStorageService
 */
@ExtendWith(MockitoExtension.class)
public class WebhookStorageServiceTest {
    
    @Mock
    private CloudEventsWebhookParser cloudEventsParser;
    
    @InjectMocks
    private WebhookStorageService webhookStorageService;
    
    @BeforeEach
    public void setUp() {
        webhookStorageService.clearWebhooks();
    }
    
    @Test
    public void testAddWebhook_ValidPayload() {
        // Arrange
        String payload = "[{\"id\":\"test-1\",\"type\":\"qbo.customer.created.v1\"}]";
        List<WebhooksCloudEvents> mockEvents = createMockEvents(1);
        when(cloudEventsParser.parseCloudEvents(anyString())).thenReturn(mockEvents);
        
        // Act
        webhookStorageService.addWebhook(payload);
        
        // Assert
        assertEquals(1, webhookStorageService.getTotalEventCount());
        List<WebhooksCloudEvents> storedEvents = webhookStorageService.getRecentCloudEvents();
        assertEquals(1, storedEvents.size());
        verify(cloudEventsParser, times(1)).parseCloudEvents(payload);
    }
    
    @Test
    public void testAddWebhook_NullPayload() {
        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            webhookStorageService.addWebhook(null);
        });
        assertNotNull(exception);
    }
    
    @Test
    public void testAddWebhook_EmptyPayload() {
        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            webhookStorageService.addWebhook("");
        });
        assertNotNull(exception);
    }
    
    @Test
    public void testAddWebhook_WhitespacePayload() {
        // Act & Assert
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            webhookStorageService.addWebhook("   ");
        });
        assertNotNull(exception);
    }
    
    @Test
    public void testAddWebhook_MultipleEvents() {
        // Arrange
        String payload = "[{\"id\":\"test-1\"},{\"id\":\"test-2\"},{\"id\":\"test-3\"}]";
        List<WebhooksCloudEvents> mockEvents = createMockEvents(3);
        when(cloudEventsParser.parseCloudEvents(anyString())).thenReturn(mockEvents);
        
        // Act
        webhookStorageService.addWebhook(payload);
        
        // Assert
        assertEquals(3, webhookStorageService.getTotalEventCount());
    }
    
    @Test
    public void testAddWebhook_EmptyEventList() {
        // Arrange
        String payload = "[]";
        when(cloudEventsParser.parseCloudEvents(anyString())).thenReturn(new ArrayList<>());
        
        // Act
        webhookStorageService.addWebhook(payload);
        
        // Assert
        assertEquals(0, webhookStorageService.getTotalEventCount());
    }
    
    // Test 4: Idempotency — duplicate event id must be skipped, storage count must not increase
    @Test
    public void testAddWebhook_DuplicateId_Skipped() {
        // Arrange
        String payload = "[{\"id\":\"dup-001\",\"type\":\"qbo.customer.created.v1\"}]";
        List<WebhooksCloudEvents> mockEvents = createMockEventsWithId("dup-001");
        when(cloudEventsParser.parseCloudEvents(anyString())).thenReturn(mockEvents);
        
        // Act - send the same event id twice
        webhookStorageService.addWebhook(payload);
        webhookStorageService.addWebhook(payload);
        
        // Assert - only first event stored; second was deduplicated
        assertEquals(1, webhookStorageService.getTotalEventCount());
        verify(cloudEventsParser, times(2)).parseCloudEvents(payload);
    }
    
    @Test
    public void testAddWebhook_ExceedsMaxCapacity() {
        // Arrange
        int maxCapacity = webhookStorageService.getMaxCapacity();
        AtomicInteger counter = new AtomicInteger(0);
        when(cloudEventsParser.parseCloudEvents(anyString())).thenAnswer(inv ->
            createMockEventsWithId("test-" + counter.getAndIncrement()));
        
        // Act - Add more than max capacity
        for (int i = 0; i < maxCapacity + 5; i++) {
            webhookStorageService.addWebhook("[{\"id\":\"test-" + i + "\"}]");
        }
        
        // Assert - Should maintain max capacity
        assertEquals(maxCapacity, webhookStorageService.getTotalEventCount());
    }
    
    @Test
    public void testAddWebhook_ParseException() {
        // Arrange
        String payload = "invalid json";
        when(cloudEventsParser.parseCloudEvents(anyString()))
            .thenThrow(new RuntimeException("Parse error"));
        
        // Act & Assert
        Exception exception = assertThrows(RuntimeException.class, () -> {
            webhookStorageService.addWebhook(payload);
        });
        assertNotNull(exception);
    }
    
    @Test
    public void testClearWebhooks() {
        // Arrange - Add some webhooks first
        List<WebhooksCloudEvents> mockEvents = createMockEvents(5);
        when(cloudEventsParser.parseCloudEvents(anyString())).thenReturn(mockEvents);
        webhookStorageService.addWebhook("[{\"id\":\"test\"}]");
        assertEquals(5, webhookStorageService.getTotalEventCount());
        
        // Act
        webhookStorageService.clearWebhooks();
        
        // Assert
        assertEquals(0, webhookStorageService.getTotalEventCount());
        assertTrue(webhookStorageService.getRecentCloudEvents().isEmpty());
    }
    
    @Test
    public void testGetRecentCloudEvents_ReturnsUnmodifiableList() {
        // Arrange
        List<WebhooksCloudEvents> mockEvents = createMockEvents(2);
        when(cloudEventsParser.parseCloudEvents(anyString())).thenReturn(mockEvents);
        webhookStorageService.addWebhook("[{\"id\":\"test\"}]");
        
        // Act
        List<WebhooksCloudEvents> events = webhookStorageService.getRecentCloudEvents();
        
        // Assert - Attempt to modify should throw exception
        Exception exception = assertThrows(UnsupportedOperationException.class, () -> {
            events.clear();
        });
        assertNotNull(exception);
    }
    
    @Test
    public void testGetRecentCloudEvents_MostRecentFirst() {
        // Arrange
        for (int i = 0; i < 3; i++) {
            List<WebhooksCloudEvents> mockEvents = createMockEventsWithId("test-" + i);
            when(cloudEventsParser.parseCloudEvents(anyString())).thenReturn(mockEvents);
            webhookStorageService.addWebhook("[{\"id\":\"test-" + i + "\"}]");
        }
        
        // Act
        List<WebhooksCloudEvents> events = webhookStorageService.getRecentCloudEvents();
        
        // Assert - Most recent (test-2) should be first
        assertEquals(3, events.size());
        assertEquals("test-2", events.get(0).getId());
        assertEquals("test-0", events.get(2).getId());
    }
    
    @Test
    public void testGetTotalEventCount_EmptyStorage() {
        // Act & Assert
        assertEquals(0, webhookStorageService.getTotalEventCount());
    }
    
    @Test
    public void testGetMaxCapacity() {
        // Act & Assert
        assertEquals(50, webhookStorageService.getMaxCapacity());
    }
    
    @Test
    public void testConcurrentAccess() throws InterruptedException {
        // Arrange
        AtomicInteger counter = new AtomicInteger(0);
        when(cloudEventsParser.parseCloudEvents(anyString())).thenAnswer(inv ->
            createMockEventsWithId("test-" + counter.getAndIncrement()));
        
        // Act - Simulate concurrent webhook additions
        Thread thread1 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                webhookStorageService.addWebhook("[{\"id\":\"thread1-" + i + "\"}]");
            }
        });
        
        Thread thread2 = new Thread(() -> {
            for (int i = 0; i < 10; i++) {
                webhookStorageService.addWebhook("[{\"id\":\"thread2-" + i + "\"}]");
            }
        });
        
        thread1.start();
        thread2.start();
        thread1.join();
        thread2.join();
        
        // Assert - Should have exactly 20 events without corruption
        assertEquals(20, webhookStorageService.getTotalEventCount());
    }
    
    // Helper methods
    
    private List<WebhooksCloudEvents> createMockEvents(int count) {
        List<WebhooksCloudEvents> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            WebhooksCloudEvents event = mock(WebhooksCloudEvents.class);
            when(event.getId()).thenReturn("test-id-" + i);
            when(event.getType()).thenReturn("qbo.customer.created.v1");
            when(event.getIntuitEntityId()).thenReturn(String.valueOf(i));
            when(event.getIntuitAccountId()).thenReturn("123456");
            events.add(event);
        }
        return events;
    }
    
    private List<WebhooksCloudEvents> createMockEventsWithId(String id) {
        List<WebhooksCloudEvents> events = new ArrayList<>();
        WebhooksCloudEvents event = mock(WebhooksCloudEvents.class);
        when(event.getId()).thenReturn(id);
        when(event.getType()).thenReturn("qbo.customer.created.v1");
        events.add(event);
        return events;
    }
}
