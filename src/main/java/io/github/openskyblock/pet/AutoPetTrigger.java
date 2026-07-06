package io.github.openskyblock.pet;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum AutoPetTrigger {
    LOGIN("login", "Login"),
    KILL("kill", "Kill"),
    BLOCK_BREAK("block_break", "Block Break"),
    ITEM_HELD("item_held", "Held Item Change");

    private final String key;
    private final String displayName;

    AutoPetTrigger(String key, String displayName) {
        this.key = key;
        this.displayName = displayName;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public static Optional<AutoPetTrigger> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String normalized = raw.toUpperCase(Locale.ROOT).replace('-', '_');
        return Arrays.stream(values())
                .filter(trigger -> trigger.name().equals(normalized) || trigger.key.equalsIgnoreCase(raw))
                .findFirst();
    }
}
