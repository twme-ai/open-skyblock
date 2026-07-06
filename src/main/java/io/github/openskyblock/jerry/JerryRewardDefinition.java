package io.github.openskyblock.jerry;

import org.bukkit.Material;

public record JerryRewardDefinition(
        String type,
        String itemId,
        Material material,
        int amount,
        double coins,
        double weight
) {
    public boolean coinsReward() {
        return type.equals("COINS");
    }

    public boolean customItem() {
        return type.equals("CUSTOM_ITEM");
    }
}
