package com.intuit.developer.sampleapp.webhooks.controller;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Objects;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.AnnotationConfigWebContextLoader;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intuit.developer.sampleapp.webhooks.controllers.WebhooksController;
import com.intuit.developer.sampleapp.webhooks.service.WebhookStorageService;
import com.intuit.ipp.services.WebhooksService;

/**
 * @author dderose
 *
 */
@ExtendWith(SpringExtension.class)
@WebAppConfiguration
@ContextConfiguration(loader=AnnotationConfigWebContextLoader.class)
public class WebhooksControllerTest {
	
	private MockMvc mockMvc;
	private WebhooksController webhooksController;
	private Environment envMock;
	private WebhookStorageService webhookStorageServiceMock;
	private WebhooksService webhooksServiceMock;
	
	public static final MediaType APPLICATION_JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON.getType(), MediaType.APPLICATION_JSON.getSubtype(), Charset.forName("utf8"));


	
	@Autowired
	protected WebApplicationContext wac;
	
	@BeforeEach
	public void setUp() throws Exception {
		MockitoAnnotations.openMocks(this);
		
		webhooksController = new WebhooksController();
		envMock = Mockito.mock(Environment.class);
		ReflectionTestUtils.setField(Objects.requireNonNull(webhooksController), "env", envMock);
		
		webhookStorageServiceMock = Mockito.mock(WebhookStorageService.class);
		ReflectionTestUtils.setField(Objects.requireNonNull(webhooksController), "webhookStorageService", webhookStorageServiceMock);
		
		webhooksServiceMock = Mockito.mock(WebhooksService.class);
		ReflectionTestUtils.setField(Objects.requireNonNull(webhooksController), "webhooksService", webhooksServiceMock);
		
		Mockito.when(envMock.getProperty("quickbooks.webhooks-verifier-token")).thenReturn("test-token");
		Mockito.doNothing().when(webhookStorageServiceMock).addWebhook(Mockito.anyString());
		Mockito.when(webhooksServiceMock.verifyPayload(Mockito.anyString(), Mockito.anyString())).thenReturn(true);
		
		mockMvc = MockMvcBuilders.standaloneSetup(webhooksController).build();
		
	}

	@Test
	public void webhooks() throws Exception {
		
		String payload = "{}";
		
		mockMvc.perform(post("/webhooks")
		.contentType(APPLICATION_JSON_UTF8)
		.content(convertObjectToJsonBytes(payload))
		.header("intuit-signature", "1234"))
		.andExpect(status().isOk());
		
	}
	
	@Test
	public void webhooksWithWrongSignature() throws Exception {
		
		String payload = "{}";
		
		mockMvc.perform(post("/webhooks")
		.contentType(APPLICATION_JSON_UTF8)
		.content(convertObjectToJsonBytes(payload))
		.header("intuit-signature", ""))
		.andExpect(status().isForbidden());
		
	}
	
	@Test
	public void webhooksWithMissingSignature() throws Exception {
		// Missing signature should return 403
		String payload = "[{\"id\":\"test\"}]";
		mockMvc.perform(post("/webhooks")
		.contentType(APPLICATION_JSON_UTF8)
		.content(payload))
		.andExpect(status().isForbidden());
	}
	
	@Test
	public void webhooksWithNoSignatureHeader() throws Exception {
		// No signature header should return 403 Forbidden
		mockMvc.perform(post("/webhooks")
		.contentType(APPLICATION_JSON_UTF8)
		.content("{}"))
		.andExpect(status().isForbidden());
	}
	
	@Test
	public void webhooksWithInvalidSignature() throws Exception {
		// Mock invalid signature validation
		String payload = "[{\"specversion\":\"1.0\"}]";
		Mockito.when(webhooksServiceMock.verifyPayload(Mockito.anyString(), Mockito.anyString())).thenReturn(false);
		
		mockMvc.perform(post("/webhooks")
		.contentType(APPLICATION_JSON_UTF8)
		.content(convertObjectToJsonBytes(payload))
		.header("intuit-signature", "invalid-sig"))
		.andExpect(status().isForbidden());
	}
	
	@Test
	public void webhooksWithStorageServiceException() throws Exception {
		// Mock storage service throwing exception
		String payload = "[{\"specversion\":\"1.0\"}]";
		Mockito.doThrow(new RuntimeException("Storage failed"))
			.when(webhookStorageServiceMock).addWebhook(Mockito.anyString());
		
		mockMvc.perform(post("/webhooks")
		.contentType(APPLICATION_JSON_UTF8)
		.content(convertObjectToJsonBytes(payload))
		.header("intuit-signature", "1234"))
		.andExpect(status().isOk());
	}
	
	@Test
	public void webhooksWithValidCloudEventsPayload() throws Exception {
		String payload = "[{\"specversion\":\"1.0\",\"id\":\"test-123\",\"type\":\"qbo.customer.created.v1\"}]";
		
		mockMvc.perform(post("/webhooks")
		.contentType(APPLICATION_JSON_UTF8)
		.content(payload)
		.header("intuit-signature", "1234"))
		.andExpect(status().isOk());
		
		Mockito.verify(webhookStorageServiceMock, Mockito.times(1)).addWebhook(payload);
	}
	
	@Test
	public void testWebhookEndpoint() throws Exception {
		String payload = "{\"test\":\"data\"}";
		
		mockMvc.perform(post("/webhooks/test")
		.contentType(APPLICATION_JSON_UTF8)
		.content(payload))
		.andExpect(status().isOk());
	}
	
	@Test
	public void webhooksWithCloudEventsContentType() throws Exception {
		String payload = "[{\"specversion\":\"1.0\",\"id\":\"test-456\",\"type\":\"qbo.customer.created.v1\",\"intuitentityid\":\"42\",\"intuitaccountid\":\"123456\"}]";
		
		mockMvc.perform(post("/webhooks")
		.contentType("application/cloudevents+json")
		.content(payload)
		.header("intuit-signature", "1234"))
		.andExpect(status().isOk());
		
		Mockito.verify(webhookStorageServiceMock, Mockito.times(1)).addWebhook(payload);
	}
	
	@Test
	public void webhooksWithMalformedJsonBody() throws Exception {
		String payload = "[{broken json";
		
		mockMvc.perform(post("/webhooks")
		.contentType(APPLICATION_JSON_UTF8)
		.content(payload)
		.header("intuit-signature", "1234"))
		.andExpect(status().isOk());
	}
	
	@Test
	public void webhooksWithUnknownEventType() throws Exception {
		String payload = "[{\"specversion\":\"1.0\",\"id\":\"test-999\",\"type\":\"qbo.unknown.entity.v1\",\"intuitentityid\":\"99\",\"intuitaccountid\":\"123456\"}]";
		
		mockMvc.perform(post("/webhooks")
		.contentType(APPLICATION_JSON_UTF8)
		.content(payload)
		.header("intuit-signature", "1234"))
		.andExpect(status().isOk());
		
		Mockito.verify(webhookStorageServiceMock, Mockito.times(1)).addWebhook(payload);
	}

	public  byte[] convertObjectToJsonBytes(Object object) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL);
        return mapper.writeValueAsBytes(object);
    }
}
