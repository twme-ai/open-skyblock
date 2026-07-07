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
    QUIVER,
    STORAGE,
    ACCESSORY_BAG,
    TUNING,
    EQUIPMENT,
    WARDROBE,
    REFORGE_ANVIL,
    ENCHANTING_TABLE,
    ENCHANTING_ANVIL,
    PETS,
    COLLECTIONS,
    RECIPES,
    SHOPS,
    AUCTIONS,
    BAZAAR,
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
