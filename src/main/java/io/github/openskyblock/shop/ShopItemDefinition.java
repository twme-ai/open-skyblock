package io.github.openskyblock.shop;

import org.bukkit.Material;

public record ShopItemDefinition(
        String id,
        int slot,
        Material material,
        String displayName,
        int amount,
        double buyPrice,
        double sellPrice,
        int dailyBuyLimit
) {
}
