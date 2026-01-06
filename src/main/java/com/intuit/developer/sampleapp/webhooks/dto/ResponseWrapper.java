package com.intuit.developer.sampleapp.webhooks.dto;

/**
 * Simple response wrapper for API endpoints
 */
public class ResponseWrapper {
    
    private String status;
    private String message;
    
    public ResponseWrapper(String status) {
        this.status = status;
    }
    
    public ResponseWrapper(String status, String message) {
        this.status = status;
        this.message = message;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getMessage() {
        return message;
    }
    
    public void setMessage(String message) {
        this.message = message;
    }
}
