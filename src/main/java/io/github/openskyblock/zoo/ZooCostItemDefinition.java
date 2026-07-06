package io.github.openskyblock.zoo;

import org.bukkit.Material;

public record ZooCostItemDefinition(
        String type,
        String itemId,
        Material material,
        int amount
) {
    public boolean customItem() {
        return type.equals("CUSTOM_ITEM");
    }
}
