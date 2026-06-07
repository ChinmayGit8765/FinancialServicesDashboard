package com.quantlens.portfolio.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * JPA entity for the {@code app_users} table.
 * <p>
 * Demo users seeded by {@code SeedRunner}: alice (Growth), bob (Income), charlie (Balanced).
 * Passwords are BCrypt-hashed by the single canonical {@code PasswordEncoder} bean.
 */
@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String persona;

    /** Nullable — reserved for future OAuth sub claim. */
    @Column(name = "external_id", length = 255)
    private String externalId;

    // ── constructors ──────────────────────────────────────────────────────────

    protected AppUser() {
    }

    public AppUser(String username, String passwordHash, String persona) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.persona = persona;
    }

    // ── accessors ─────────────────────────────────────────────────────────────

    public Long getId() { return id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getPersona() { return persona; }
    public void setPersona(String persona) { this.persona = persona; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }
}
