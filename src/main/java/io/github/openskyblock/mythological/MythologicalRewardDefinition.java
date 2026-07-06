package io.github.openskyblock.mythological;

import io.github.openskyblock.service.Rarity;
import org.bukkit.Material;

public record MythologicalRewardDefinition(
        String type,
        String itemId,
        Material material,
        int amount,
        double coins,
        double weight,
        Rarity minimumRarity
) {
    public boolean coinsReward() {
        return type.equals("COINS");
    }

    public boolean customItem() {
        return type.equals("CUSTOM_ITEM");
    }
}
