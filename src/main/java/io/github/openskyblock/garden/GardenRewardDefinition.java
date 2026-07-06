package io.github.openskyblock.garden;

import org.bukkit.Material;

public record GardenRewardDefinition(
        String type,
        String itemId,
        Material material,
        int amount,
        double coins,
        double weight
) {
    public boolean customItem() {
        return type.equals("CUSTOM_ITEM");
    }

    public boolean coinsReward() {
        return type.equals("COINS");
    }
}
