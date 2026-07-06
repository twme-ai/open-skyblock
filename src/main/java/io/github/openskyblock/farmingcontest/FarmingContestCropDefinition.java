package io.github.openskyblock.farmingcontest;

import org.bukkit.Material;

public record FarmingContestCropDefinition(
        String id,
        String displayName,
        Material material,
        String collectionId,
        double weight
) {
}
