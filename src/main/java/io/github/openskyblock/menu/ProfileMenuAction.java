package io.github.openskyblock.menu;

import java.util.Locale;

public enum ProfileMenuAction {
    NONE,
    BACK,
    SKILLS,
    COLLECTIONS,
    STATS,
    BANK,
    PETS,
    UPGRADES;

    public static ProfileMenuAction parse(String value) {
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
