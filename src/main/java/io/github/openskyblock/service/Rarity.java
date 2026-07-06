package io.github.openskyblock.service;

import java.util.Locale;

public enum Rarity {
    COMMON("<white>"),
    UNCOMMON("<green>"),
    RARE("<blue>"),
    EPIC("<dark_purple>"),
    LEGENDARY("<gold>"),
    MYTHIC("<light_purple>"),
    SPECIAL("<red>");

    private final String colorTag;

    Rarity(String colorTag) {
        this.colorTag = colorTag;
    }

    public String colorTag() {
        return colorTag;
    }

    public static Rarity parse(String value) {
        if (value == null || value.isBlank()) {
            return COMMON;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return COMMON;
        }
    }
}
