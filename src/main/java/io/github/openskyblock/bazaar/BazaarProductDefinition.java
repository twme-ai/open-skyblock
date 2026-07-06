package io.github.openskyblock.bazaar;

import org.bukkit.Material;

public record BazaarProductDefinition(
        String id,
        String category,
        Material material,
        String customItemId,
        String displayName,
        int stackSize
) {
    public boolean customItem() {
        return customItemId != null && !customItemId.isBlank();
    }
}
