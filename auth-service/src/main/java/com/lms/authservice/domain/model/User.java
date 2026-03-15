package com.lms.authservice.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * User — the core aggregate root for the auth domain.
 *
 * <p>This is a plain domain object with no JPA or Spring annotations.
 * Business invariants are enforced here, not in the infrastructure layer.
 *
 * <p>Passwords are stored as BCrypt hashes — the domain model never
 * deals with plaintext passwords; that responsibility belongs to the
 * application service before passing data into the domain.
 */
public class User {

    private final UUID id;
    private String email;
    private String passwordHash;
    private String firstName;
    private String lastName;
    private Role role;
    private boolean enabled;
    private boolean emailVerified;
    private Instant createdAt;
    private Instant updatedAt;
    private int failedLoginAttempts;
    private Instant lockedUntil;

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final int LOCK_DURATION_MINUTES = 15;

    // ── Construction ────────────────────────────────────────

    private User(UUID id, String email, String passwordHash,
                 String firstName, String lastName, Role role) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.role = role;
        this.enabled = true;
        this.emailVerified = false;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
        this.failedLoginAttempts = 0;
    }

    /**
     * Factory method — the only way to create a new User in the domain.
     * Enforces invariants: email must be non-null, password must be pre-hashed.
     */
    public static User create(String email, String passwordHash,
                              String firstName, String lastName, Role role) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email must not be blank");
        }
        if (passwordHash == null || passwordHash.isBlank()) {
            throw new IllegalArgumentException("Password hash must not be blank");
        }
        return new User(UUID.randomUUID(), email.toLowerCase().strip(),
                passwordHash, firstName, lastName, role);
    }

    /**
     * Reconstitution — used by the repository to rebuild a User from persistence.
     * Does NOT enforce creation invariants (data is already validated on entry).
     */
    public static User reconstitute(UUID id, String email, String passwordHash,
                                    String firstName, String lastName, Role role,
                                    boolean enabled, boolean emailVerified,
                                    Instant createdAt, Instant updatedAt,
                                    int failedLoginAttempts, Instant lockedUntil) {
        User user = new User(id, email, passwordHash, firstName, lastName, role);
        user.enabled = enabled;
        user.emailVerified = emailVerified;
        user.createdAt = createdAt;
        user.updatedAt = updatedAt;
        user.failedLoginAttempts = failedLoginAttempts;
        user.lockedUntil = lockedUntil;
        return user;
    }

    // ── Business Behaviour ───────────────────────────────────

    public boolean isAccountLocked() {
        if (lockedUntil == null) return false;
        if (Instant.now().isAfter(lockedUntil)) {
            // Lock has expired — auto-unlock
            lockedUntil = null;
            failedLoginAttempts = 0;
            return false;
        }
        return true;
    }

    public void recordFailedLogin() {
        this.failedLoginAttempts++;
        this.updatedAt = Instant.now();
        if (this.failedLoginAttempts >= MAX_FAILED_ATTEMPTS) {
            this.lockedUntil = Instant.now()
                .plusSeconds(LOCK_DURATION_MINUTES * 60L);
        }
    }

    public void recordSuccessfulLogin() {
        this.failedLoginAttempts = 0;
        this.lockedUntil = null;
        this.updatedAt = Instant.now();
    }

    public void verifyEmail() {
        this.emailVerified = true;
        this.updatedAt = Instant.now();
    }

    public void changePassword(String newPasswordHash) {
        if (newPasswordHash == null || newPasswordHash.isBlank()) {
            throw new IllegalArgumentException("New password hash must not be blank");
        }
        this.passwordHash = newPasswordHash;
        this.updatedAt = Instant.now();
    }

    public void updateProfile(String firstName, String lastName) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.updatedAt = Instant.now();
    }

    public void disable() {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

    public String fullName() {
        return firstName + " " + lastName;
    }

    // ── Getters (no setters — mutations go through domain methods) ─

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public Role getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public boolean isEmailVerified() { return emailVerified; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getFailedLoginAttempts() { return failedLoginAttempts; }
    public Instant getLockedUntil() { return lockedUntil; }
}
