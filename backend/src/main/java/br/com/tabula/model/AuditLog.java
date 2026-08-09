package br.com.tabula.model;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public final class AuditLog {
    private final Long id;
    private final Long userDatabaseId;
    private final String actorExternalId;
    private final String action;
    private final String resourceType;
    private final String resourceId;
    private final JsonNode details;
    private final String ipAddress;
    private final String userAgent;
    private final boolean success;
    private final String traceId;
    private final Instant createdAt;
    private final String actorName;
    private final String actorEmail;

    public AuditLog(
            Long id,
            Long userDatabaseId,
            String actorExternalId,
            String action,
            String resourceType,
            String resourceId,
            JsonNode details,
            String ipAddress,
            String userAgent,
            boolean success,
            String traceId,
            Instant createdAt) {
        this(id, userDatabaseId, actorExternalId, action, resourceType, resourceId, details, ipAddress, userAgent, success, traceId, createdAt, null, null);
    }

    public AuditLog(
            Long id,
            Long userDatabaseId,
            String actorExternalId,
            String action,
            String resourceType,
            String resourceId,
            JsonNode details,
            String ipAddress,
            String userAgent,
            boolean success,
            String traceId,
            Instant createdAt,
            String actorName,
            String actorEmail) {
        this.id = id;
        this.userDatabaseId = userDatabaseId;
        this.actorExternalId = actorExternalId;
        this.action = action;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.details = details;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.success = success;
        this.traceId = traceId;
        this.createdAt = createdAt;
        this.actorName = actorName;
        this.actorEmail = actorEmail;
    }

    public Long getId() { return id; }
    public Long getUserDatabaseId() { return userDatabaseId; }
    public String getActorExternalId() { return actorExternalId; }
    public String getAction() { return action; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public JsonNode getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public boolean isSuccess() { return success; }
    public String getTraceId() { return traceId; }
    public Instant getCreatedAt() { return createdAt; }
    public String getActorName() { return actorName; }
    public String getActorEmail() { return actorEmail; }
}
