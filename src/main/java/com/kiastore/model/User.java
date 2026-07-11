package com.kiastore.model;

import java.time.LocalDateTime;

/**
 * Represents a user in the KIA Store ERP.
 */
public class User {

    private int id;
    private String name;
    private String username;
    private String passwordHash;
    private Role role;
    private boolean isActive = true;
    private LocalDateTime createdAt;

    public User() {}

    public User(int id, String username, String passwordHash, String name, Role role, boolean isActive) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.isActive = isActive;
    }

    public int getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getName() { return name; }
    public Role getRole() { return role; }
    public boolean isActive() { return isActive; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(int id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public void setName(String name) { this.name = name; }
    public void setRole(Role role) { this.role = role; }
    public void setActive(boolean active) { this.isActive = active; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override 
    public String toString() { 
        return name + " (" + username + " / " + role + ")"; 
    }
}
