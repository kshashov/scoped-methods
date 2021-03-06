package io.github.kshashov.scopedmethods;

import javax.validation.constraints.NotNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Stack;

public class ScopedMethodsHolder {
    static final ThreadLocal<Map<String, Stack<String>>> ACTIVE_SCOPES = ThreadLocal.withInitial(HashMap::new);

    /**
     * Shortcut for {@link #getCurrent(String)} for empty group.
     *
     * @return the current scope
     */
    public static String getCurrent() {
        return getCurrent("");
    }

    /**
     * Shortcut for {@link #contains(String, String)} with the empty group.
     *
     * @param key scope whose presence is to be tested
     * @return {@code true} if the empty group contains the specified scope
     */
    public static boolean contains(@NotNull String key) {
        return contains(key);
    }

    /**
     * Returns {@code true} if the specified group contains the specified scope.
     *
     * @param group group id
     * @param key   scope whose presence is to be tested
     * @return {@code true} if the specified group contains the specified scope
     */
    public static boolean contains(@NotNull String group, @NotNull String key) {
        Objects.requireNonNull(group);
        Objects.requireNonNull(key);
        Map<String, Stack<String>> scopes = ACTIVE_SCOPES.get();

        Stack<String> groupScopes = scopes.get(group);
        if ((groupScopes == null) || groupScopes.empty()) {
            return false;
        }

        return groupScopes.contains(key);
    }

    /**
     * Returns the current scope id in the specified group.
     *
     * @param group group id
     * @return the current scope id for the specified group or {@code null} if nothing
     */
    public static String getCurrent(@NotNull String group) {
        Objects.requireNonNull(group);
        Map<String, Stack<String>> scopes = ACTIVE_SCOPES.get();

        Stack<String> groupScopes = scopes.get(group);
        if ((groupScopes == null) || groupScopes.empty()) {
            return null;
        }

        return groupScopes.peek();
    }
}
