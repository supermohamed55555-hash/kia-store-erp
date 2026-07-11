package com.kiastore.model;

public enum Role {
    ADMIN, CASHIER, WAREHOUSE;

    public static Role of(String s) {
        if (s == null) return CASHIER;
        return Role.valueOf(s.trim().toUpperCase());
    }
}
