package io.github.openskyblock.service;

import org.bukkit.Material;

public record MinionUpgradeDefinition(
        String id,
        String displayName,
        Material material,
        String customItemId,
        double speedMultiplier,
        double outputMultiplier,
        long storageBonus
) {
    public boolean customItem() {
        return customItemId != null && !customItemId.isBlank();
    }
}
