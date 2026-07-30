package br.com.tabula.model;

public final class AuthenticatedPrincipal {
    private final long databaseId;
    private final String externalId;
    private final String role;

    public AuthenticatedPrincipal(long databaseId, String externalId, String role) {
        this.databaseId = databaseId;
        this.externalId = externalId;
        this.role = role;
    }

    public long getDatabaseId() {
        return databaseId;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getRole() {
        return role;
    }

    public boolean isAdmin() {
        return "ADMIN".equalsIgnoreCase(role);
    }
}
