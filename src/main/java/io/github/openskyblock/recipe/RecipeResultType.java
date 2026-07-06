package io.github.openskyblock.recipe;

import java.util.Locale;

public enum RecipeResultType {
    VANILLA,
    CUSTOM_ITEM,
    MINION;

    public static RecipeResultType parse(String value) {
        if (value == null || value.isBlank()) {
            return VANILLA;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return VANILLA;
        }
    }
}
