package io.github.openskyblock.forge;

import org.bukkit.Material;

public record ForgeItemDefinition(
        String type,
        String itemId,
        Material material,
        int amount
) {
    public boolean customItem() {
        return "CUSTOM_ITEM".equalsIgnoreCase(type);
    }

    public String key() {
        return customItem() ? itemId : material.name();
    }
}
