package io.github.openskyblock.menu;

import java.util.Locale;

public enum IslandMenuAction {
    NONE,
    HOME,
    SET_HOME,
    TOGGLE_VISITORS,
    INFO,
    BACK;

    public static IslandMenuAction parse(String value) {
        if (value == null || value.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NONE;
        }
    }
}
