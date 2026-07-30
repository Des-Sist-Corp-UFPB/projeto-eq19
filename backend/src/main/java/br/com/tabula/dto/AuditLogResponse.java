package br.com.tabula.dto;

import br.com.tabula.model.AuditLog;
import com.fasterxml.jackson.databind.JsonNode;

public final class AuditLogResponse {
    private final long id;
    private final String userId;
    private final String action;
    private final String resourceType;
    private final String resourceId;
    private final JsonNode details;
    private final String ipAddress;
    private final String userAgent;
    private final boolean success;
    private final String traceId;
    private final String createdAt;

    public AuditLogResponse(AuditLog log) {
        this.id = log.getId();
        this.userId = log.getActorExternalId();
        this.action = log.getAction();
        this.resourceType = log.getResourceType();
        this.resourceId = log.getResourceId();
        this.details = log.getDetails();
        this.ipAddress = log.getIpAddress();
        this.userAgent = log.getUserAgent();
        this.success = log.isSuccess();
        this.traceId = log.getTraceId();
        this.createdAt = log.getCreatedAt().toString();
    }

    public long getId() { return id; }
    public String getUserId() { return userId; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public JsonNode getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public boolean isSuccess() { return success; }
    public String getTraceId() { return traceId; }
    public String getCreatedAt() { return createdAt; }
}
