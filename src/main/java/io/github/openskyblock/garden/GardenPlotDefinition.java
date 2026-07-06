package io.github.openskyblock.garden;

public record GardenPlotDefinition(
        String id,
        String displayName,
        int requiredGardenLevel,
        long compostCost,
        double coinCost,
        boolean unlockedByDefault
) {
}
