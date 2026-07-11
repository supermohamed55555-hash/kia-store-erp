package com.kiastore.model;

import java.time.LocalDateTime;

/**
 * Represents a parts supplier.
 */
public class Supplier {

    private int id;
    private String name;
    private String phone;
    private String address;
    private String notes;
    private LocalDateTime createdAt;

    public Supplier() {}

    public Supplier(int id, String name, String phone, String address, String notes) {
        this.id = id;
        this.name = name;
        this.phone = phone;
        this.address = address;
        this.notes = notes;
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public String getAddress() { return address; }
    public String getNotes() { return notes; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setAddress(String address) { this.address = address; }
    public void setNotes(String notes) { this.notes = notes; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    @Override
    public String toString() {
        return name;
    }
}
