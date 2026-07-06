package io.github.openskyblock.chocolate;

public record ChocolateUpgradeDefinition(
        String id,
        String displayName,
        int maxLevel,
        double baseCost,
        double costMultiplier,
        double chocolatePerSecond,
        double productionMultiplier,
        double clickChocolate,
        double capacity
) {
}
