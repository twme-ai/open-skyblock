package io.github.openskyblock.garden;

import org.bukkit.Material;

public record GardenCropDefinition(
        String id,
        String displayName,
        Material material,
        String collectionId,
        long milestoneInterval,
        int maxMilestone,
        double farmingXpPerHarvest,
        double gardenXpPerMilestone,
        long copperPerMilestone
) {
}
