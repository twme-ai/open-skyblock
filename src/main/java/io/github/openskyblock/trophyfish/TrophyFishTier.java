package io.github.openskyblock.trophyfish;

import java.util.Locale;
import java.util.Optional;

public enum TrophyFishTier {
    DIAMOND,
    GOLD,
    SILVER,
    BRONZE;

    public static Optional<TrophyFishTier> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(raw.toUpperCase(Locale.ROOT).replace('-', '_')));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }
}
