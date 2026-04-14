package com.intuit.developer.sampleapp.webhooks.domain;

/**
 * Represents a parsed webhook event from QuickBooks.
 * Stores the key fields from a legacy webhook notification.
 */
public class WebhookEvent {
    
    private String realmId;
    private String entityName;
    private String entityId;
    private String operation;
    private String lastUpdated;
    
    public WebhookEvent() {
    }
    
    public WebhookEvent(String realmId, String entityName, String entityId, String operation, String lastUpdated) {
        this.realmId = realmId;
        this.entityName = entityName;
        this.entityId = entityId;
        this.operation = operation;
        this.lastUpdated = lastUpdated;
    }
    
    public String getRealmId() {
        return realmId;
    }
    
    public void setRealmId(String realmId) {
        this.realmId = realmId;
    }
    
    public String getEntityName() {
        return entityName;
    }
    
    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }
    
    public String getEntityId() {
        return entityId;
    }
    
    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }
    
    public String getOperation() {
        return operation;
    }
    
    public void setOperation(String operation) {
        this.operation = operation;
    }
    
    public String getLastUpdated() {
        return lastUpdated;
    }
    
    public void setLastUpdated(String lastUpdated) {
        this.lastUpdated = lastUpdated;
    }
    
    @Override
    public String toString() {
        return "WebhookEvent{" +
            "realmId='" + realmId + '\'' +
            ", entityName='" + entityName + '\'' +
            ", entityId='" + entityId + '\'' +
            ", operation='" + operation + '\'' +
            ", lastUpdated='" + lastUpdated + '\'' +
            '}';
    }
}
