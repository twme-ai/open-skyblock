package io.github.openskyblock.mob;

import org.bukkit.Material;

public record MobDropDefinition(
        String id,
        MobDropType type,
        Material material,
        String customItemId,
        double chance,
        int minAmount,
        int maxAmount,
        boolean magicFind,
        boolean announce,
        boolean broadcast
) {
}
