package io.github.openskyblock.shop;

import org.bukkit.entity.EntityType;
import org.bukkit.entity.Villager;

public record ShopNpcDefinition(
        boolean enabled,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        EntityType entityType,
        Villager.Profession profession
) {
}
