package io.github.openskyblock.sack;

import org.bukkit.Material;

public record SackItemDefinition(
        String id,
        Material material,
        String displayName
) {
}
