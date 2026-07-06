package io.github.openskyblock.mob;

import java.util.Locale;

public enum MobDropType {
    VANILLA,
    CUSTOM_ITEM;

    public static MobDropType parse(String value) {
        if (value == null || value.isBlank()) {
            return VANILLA;
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return VANILLA;
        }
    }
}
