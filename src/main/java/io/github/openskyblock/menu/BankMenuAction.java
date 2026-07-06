package io.github.openskyblock.menu;

import java.util.Locale;

public enum BankMenuAction {
    NONE,
    DEPOSIT_ALL,
    WITHDRAW_ALL;

    public static BankMenuAction parse(String value) {
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
