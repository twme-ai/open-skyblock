package io.github.openskyblock.service;

import org.bukkit.Material;

public record MinionFuelDefinition(
        String id,
        String displayName,
        Material material,
        String customItemId,
        double speedMultiplier,
        long durationSeconds
) {
    public boolean customItem() {
        return customItemId != null && !customItemId.isBlank();
    }

    public boolean permanent() {
        return durationSeconds <= 0L;
    }
}
