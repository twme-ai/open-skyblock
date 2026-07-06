package io.github.openskyblock.kuudra;

import org.bukkit.Material;

public record KuudraRewardDefinition(
        String type,
        String itemId,
        Material material,
        int amount,
        double coins,
        String essenceType,
        double essence,
        double weight,
        int minScore
) {
    public boolean customItem() {
        return type.equals("CUSTOM_ITEM");
    }

    public boolean coinsReward() {
        return type.equals("COINS");
    }

    public boolean essenceReward() {
        return type.equals("ESSENCE");
    }
}
