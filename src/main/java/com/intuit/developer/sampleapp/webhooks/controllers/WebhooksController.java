package com.intuit.developer.sampleapp.webhooks.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.intuit.developer.sampleapp.webhooks.dto.ResponseWrapper;
import com.intuit.developer.sampleapp.webhooks.service.WebhookStorageService;
import com.intuit.ipp.services.WebhooksService;
import com.intuit.ipp.util.Config;
import com.intuit.ipp.util.Logger;
import com.intuit.ipp.util.StringUtils;

/**
 * Controller class for the webhooks endpoint
 * 
 * 
 *
 */
@RestController
public class WebhooksController {
	
	private static final org.slf4j.Logger LOG = Logger.getLogger();

	private static final String SIGNATURE = "intuit-signature";
	private static final String SUCCESS = "Success";
	private static final String ERROR = "Error";
	private static final String VERIFIER_KEY = "quickbooks.webhooks-verifier-token";
	
	@Autowired
	private Environment env;
	
	@Autowired
	private WebhookStorageService webhookStorageService;
	
	@Autowired(required = false)
	private WebhooksService webhooksService;
  
    /**
     * Method to receive webhooks event notification 
     * 1. Validates payload
     * 2. Processes and stores webhook using SDK
     * 3. Sends success response back
     * 
     * @param signature
     * @param payload
     * @return
     */
    @RequestMapping(value = "/webhooks", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> webhooks(@RequestHeader(value = SIGNATURE, required = false) String signature, @RequestBody String payload) {
    	
    	LOG.info("=================================================");
    	LOG.info("WEBHOOK ENDPOINT HIT - POST REQUEST RECEIVED");
    	LOG.info("=================================================");
    	
    	try {
	    	// if signature is empty return 401
	    	if (!StringUtils.hasText(signature)) {
	    		LOG.error("Webhook received WITHOUT intuit-signature header!");
	    		LOG.error("Payload length: {}", payload != null ? payload.length() : 0);
	    		return new ResponseEntity<>(new ResponseWrapper(ERROR), HttpStatus.FORBIDDEN);
	    	}
	    	
	    	// if payload is empty, don't do anything
	    	if (!StringUtils.hasText(payload)) {
	    		LOG.warn("Webhook received with EMPTY payload");
	    		return new ResponseEntity<>(new ResponseWrapper(SUCCESS), HttpStatus.OK);
	    	}
	    	
	    	LOG.info("Webhook request received with signature and payload");
	    	LOG.info("Payload length: {} characters", payload.length());
	    	LOG.info("Payload preview: {}", payload.substring(0, Math.min(300, payload.length())));
	    	LOG.info("Signature header: {}", signature);
	    	LOG.info("Verifier token configured: {}", getVerifierToken() != null ? "[PRESENT]" : "[MISSING]");
			
			LOG.info("Starting signature validation...");
			//if request valid - process webhook
			if (validateSignature(signature, payload)) {
				LOG.info("SIGNATURE VALIDATION SUCCESSFUL!");
				LOG.info("Processing webhook through storage service...");
				// Process and store webhook using SDK
				webhookStorageService.addWebhook(payload);
				LOG.info("Webhook stored successfully!");
			} else {
				LOG.error("SIGNATURE VALIDATION FAILED!");
				LOG.error("Signature mismatch: token=[PRESENT], signature=[REDACTED]");
				LOG.error("Payload hash might not match - QuickBooks signature verification failed");
				return new ResponseEntity<>(new ResponseWrapper(ERROR), HttpStatus.FORBIDDEN);
			}
			
			LOG.info("Webhook processed and stored successfully");
	    	return new ResponseEntity<>(new ResponseWrapper(SUCCESS), HttpStatus.OK);
	    	
    	} catch (Exception e) {
    		LOG.error("Error processing webhook: {}", e.getMessage(), e);
    		return new ResponseEntity<>(new ResponseWrapper(SUCCESS), HttpStatus.OK);
    	}
    }
    
    /**
     * Validates the webhook signature using SDK WebhooksService
     */
    private boolean validateSignature(String signature, String payload) {
    	// Set verifier token in SDK config
    	Config.setProperty(Config.WEBHOOKS_VERIFIER_TOKEN, getVerifierToken());
    	// Use SDK WebhooksService to verify signature
    	WebhooksService service = webhooksService != null ? webhooksService : new WebhooksService();
    	return service.verifyPayload(signature, payload);
    }
    
    /**
     * Gets the webhook verifier token from environment properties
     */
    private String getVerifierToken() {
    	return env.getProperty(VERIFIER_KEY);
    }
    
    /**
     * Test endpoint to verify webhooks are reaching the app
     */
    @RequestMapping(value = "/webhooks/test", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> testWebhook(@RequestBody String payload) {
    	LOG.info("=================================================");
    	LOG.info("TEST WEBHOOK ENDPOINT HIT!");
    	LOG.info("=================================================");
    	LOG.info("Payload: {}", payload);
    	return new ResponseEntity<>(new ResponseWrapper(SUCCESS), HttpStatus.OK);
    }

}
