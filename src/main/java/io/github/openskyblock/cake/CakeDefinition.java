package io.github.openskyblock.cake;

import java.util.Map;
import org.bukkit.Material;

public record CakeDefinition(
        String id,
        String displayName,
        String itemId,
        Material material,
        long durationSeconds,
        Map<String, Double> stats
) {
}
