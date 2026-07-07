package io.github.openskyblock.service;

import org.bukkit.Material;

public record MinionStorageDefinition(
        String id,
        String displayName,
        Material material,
        String customItemId,
        long storageBonus
) {
    public boolean customItem() {
        return customItemId != null && !customItemId.isBlank();
    }
}
