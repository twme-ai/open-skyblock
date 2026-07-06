package io.github.openskyblock.dragon;

import org.bukkit.Material;

public record DragonRewardDefinition(
        String type,
        String itemId,
        Material material,
        int amount,
        double coins,
        double weight,
        int minEyes,
        double minDamage
) {
    public boolean customItem() {
        return type.equals("CUSTOM_ITEM");
    }

    public boolean coinsReward() {
        return type.equals("COINS");
    }
}
