package io.github.openskyblock.service;

import java.util.Map;
import org.bukkit.Material;

public record MinionUpgradeDefinition(
        String id,
        String displayName,
        Material material,
        String customItemId,
        double speedMultiplier,
        double outputMultiplier,
        long storageBonus,
        double sellPercentage,
        Map<String, MinionCompactionDefinition> compactions
) {
    public boolean customItem() {
        return customItemId != null && !customItemId.isBlank();
    }
}
