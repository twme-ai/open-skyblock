package io.github.openskyblock.backpack;

import org.bukkit.Material;

public record BackpackDefinition(
        String id,
        Material material,
        String displayName,
        int rows
) {
}
