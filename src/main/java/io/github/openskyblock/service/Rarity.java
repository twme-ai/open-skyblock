package io.github.openskyblock.service;

import java.util.Locale;

public enum Rarity {
    COMMON("<white>"),
    UNCOMMON("<green>"),
    RARE("<blue>"),
    EPIC("<dark_purple>"),
    LEGENDARY("<gold>"),
    MYTHIC("<light_purple>"),
    DIVINE("<aqua>"),
    SPECIAL("<red>"),
    VERY_SPECIAL("<red>");

    private final String colorTag;

    Rarity(String colorTag) {
        this.colorTag = colorTag;
    }

    public String colorTag() {
        return colorTag;
    }

    public Rarity next() {
        return switch (this) {
            case COMMON -> UNCOMMON;
            case UNCOMMON -> RARE;
            case RARE -> EPIC;
            case EPIC -> LEGENDARY;
            case LEGENDARY -> MYTHIC;
            case MYTHIC -> DIVINE;
            case DIVINE -> DIVINE;
            case SPECIAL -> VERY_SPECIAL;
            case VERY_SPECIAL -> VERY_SPECIAL;
        };
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
