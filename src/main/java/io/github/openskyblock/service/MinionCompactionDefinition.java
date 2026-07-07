package io.github.openskyblock.service;

import org.bukkit.Material;

public record MinionCompactionDefinition(
        String collectionId,
        long inputAmount,
        long outputAmount,
        Material outputMaterial,
        String outputCustomItemId
) {
    public boolean customItem() {
        return outputCustomItemId != null && !outputCustomItemId.isBlank();
    }
}
