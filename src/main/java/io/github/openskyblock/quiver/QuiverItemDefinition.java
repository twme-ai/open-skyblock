package io.github.openskyblock.quiver;

import org.bukkit.Material;

public record QuiverItemDefinition(
        String id,
        Material material,
        String displayName,
        int priority
) {
}
