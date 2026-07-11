package com.kiastore.app;

import com.kiastore.model.User;

/**
 * Stores the authenticated user session.
 */
public final class Session {
    private static User current;

    private Session() {}

    public static User current() { return current; }
    public static void set(User u) { current = u; }
    public static void clear() { current = null; }
}
