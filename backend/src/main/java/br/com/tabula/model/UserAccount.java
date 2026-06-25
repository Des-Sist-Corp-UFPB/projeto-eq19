package br.com.tabula.model;

public class UserAccount {
    private final long id;
    private final String externalId;
    private final String name;
    private final String email;
    private final String passwordHash;
    private final String role;
    private final boolean emailVerificado;

    public UserAccount(long id, String externalId, String name, String email, String passwordHash, String role, boolean emailVerificado) {
        this.id = id;
        this.externalId = externalId;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.emailVerificado = emailVerificado;
    }

    public long getId() { return id; }
    public String getExternalId() { return externalId; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public boolean isEmailVerificado() { return emailVerificado; }
}
