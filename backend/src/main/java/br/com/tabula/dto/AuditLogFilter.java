package br.com.tabula.dto;

import java.time.Instant;

public final class AuditLogFilter {
    private final int page;
    private final int pageSize;
    private final String action;
    private final String userId;
    private final String resourceType;
    private final String resourceId;
    private final Boolean success;
    private final Instant startDate;
    private final Instant endDate;

    public AuditLogFilter(
            int page,
            int pageSize,
            String action,
            String userId,
            String resourceType,
            String resourceId,
            Boolean success,
            Instant startDate,
            Instant endDate) {
        this.page = page;
        this.pageSize = pageSize;
        this.action = action;
        this.userId = userId;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.success = success;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public String getAction() { return action; }
    public String getUserId() { return userId; }
    public String getResourceType() { return resourceType; }
    public String getResourceId() { return resourceId; }
    public Boolean getSuccess() { return success; }
    public Instant getStartDate() { return startDate; }
    public Instant getEndDate() { return endDate; }
    public int getOffset() { return (page - 1) * pageSize; }
}
