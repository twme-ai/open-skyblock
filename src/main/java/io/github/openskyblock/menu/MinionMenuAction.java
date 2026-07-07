package io.github.openskyblock.menu;

import java.util.Locale;

public enum MinionMenuAction {
    NONE,
    CLAIM,
    FUEL,
    UPGRADE,
    SKIN,
    PICKUP;

    public static MinionMenuAction parse(String value) {
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
