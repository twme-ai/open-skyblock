package io.github.openskyblock.chocolate;

public record ChocolateShopItemDefinition(
        String id,
        String displayName,
        String itemId,
        int amount,
        double cost,
        int requiredFactoryLevel,
        int annualStock
) {
}
