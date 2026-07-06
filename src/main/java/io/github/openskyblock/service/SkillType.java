package io.github.openskyblock.service;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum SkillType {
    FARMING,
    MINING,
    COMBAT,
    FORAGING,
    FISHING,
    ENCHANTING,
    ALCHEMY,
    TAMING,
    CARPENTRY,
    RUNECRAFTING,
    SOCIAL,
    DUNGEONEERING;

    public String key() {
        return name();
    }

    public static Optional<SkillType> fromKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        String normalized = key.toUpperCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst();
    }
}
