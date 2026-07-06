package io.github.openskyblock.chocolate;

public record ChocolateFactoryLevelDefinition(
        int level,
        String displayName,
        double productionMultiplier,
        double maxChocolate,
        double prestigeCost
) {
}
