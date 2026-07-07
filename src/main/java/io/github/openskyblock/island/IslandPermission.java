package io.github.openskyblock.island;

import java.util.Locale;
import java.util.Optional;

public enum IslandPermission {
    BUILD("build"),
    INTERACT("interact"),
    SET_HOME("set-home"),
    MANAGE_WARPS("manage-warps"),
    MANAGE_TELEPORT_PADS("manage-teleport-pads"),
    MANAGE_COOP("manage-coop"),
    RESET("reset");

    private final String configKey;

    IslandPermission(String configKey) {
        this.configKey = configKey;
    }

    public String configKey() {
        return configKey;
    }

    public static Optional<IslandPermission> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        for (IslandPermission permission : values()) {
            if (permission.name().equals(normalized) || permission.configKey().equalsIgnoreCase(value.trim())) {
                return Optional.of(permission);
            }
        }
        return Optional.empty();
    }
}
