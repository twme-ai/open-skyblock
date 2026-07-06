package io.github.openskyblock.service;

import org.bukkit.Material;

public record MinionDefinition(
        String id,
        String displayName,
        Material material,
        String generatedCollection,
        long generatedAmount,
        long intervalTicks,
        long storageSize,
        int requiredCollectionTier
) {
}
