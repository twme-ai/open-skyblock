package io.github.openskyblock.spooky;

import org.bukkit.Material;

public record SpookyRewardDefinition(
        String type,
        String itemId,
        Material material,
        int amount,
        double coins,
        double weight,
        int minimumScore
) {
    public boolean coinsReward() {
        return type.equals("COINS");
    }

    public boolean customItem() {
        return type.equals("CUSTOM_ITEM");
    }
}
