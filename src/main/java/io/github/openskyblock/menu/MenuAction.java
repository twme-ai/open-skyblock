package io.github.openskyblock.menu;

import java.util.Locale;

public enum MenuAction {
    NONE,
    PROFILE,
    ISLAND_HOME,
    BANK,
    SKILLS,
    STATS,
    SACKS,
    ACCESSORY_BAG,
    TUNING,
    EQUIPMENT,
    WARDROBE,
    PETS,
    COLLECTIONS,
    RECIPES,
    SHOPS,
    MINIONS;

    public static MenuAction parse(String value) {
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
