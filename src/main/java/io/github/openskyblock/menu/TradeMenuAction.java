package io.github.openskyblock.menu;

import java.util.Locale;

public enum TradeMenuAction {
    NONE,
    OFFER_HAND,
    READY,
    CONFIRM,
    STATUS,
    CANCEL;

    public static TradeMenuAction parse(String value) {
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
