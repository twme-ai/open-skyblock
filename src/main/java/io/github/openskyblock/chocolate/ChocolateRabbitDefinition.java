package io.github.openskyblock.chocolate;

import io.github.openskyblock.service.Rarity;

public record ChocolateRabbitDefinition(
        String id,
        String displayName,
        Rarity rarity,
        double weight,
        double chocolatePerSecond,
        double productionMultiplier,
        int requiredFactoryLevel
) {
}
