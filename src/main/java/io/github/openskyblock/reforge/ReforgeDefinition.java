package io.github.openskyblock.reforge;

import io.github.openskyblock.service.Rarity;
import java.util.Map;
import java.util.Set;

public record ReforgeDefinition(
        String id,
        String displayName,
        String prefix,
        Set<String> allowedCategories,
        double costMultiplier,
        Map<Rarity, Map<String, Double>> statsByRarity
) {
}
