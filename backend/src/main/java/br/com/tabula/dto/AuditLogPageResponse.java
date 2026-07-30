package br.com.tabula.dto;

import java.util.List;

public final class AuditLogPageResponse {
    private final List<AuditLogResponse> items;
    private final int page;
    private final int pageSize;
    private final long total;

    public AuditLogPageResponse(List<AuditLogResponse> items, int page, int pageSize, long total) {
        this.items = List.copyOf(items);
        this.page = page;
        this.pageSize = pageSize;
        this.total = total;
    }

    public List<AuditLogResponse> getItems() { return items; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public long getTotal() { return total; }
}
