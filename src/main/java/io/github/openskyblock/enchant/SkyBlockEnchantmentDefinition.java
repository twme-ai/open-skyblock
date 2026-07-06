package io.github.openskyblock.enchant;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record SkyBlockEnchantmentDefinition(
        String id,
        String displayName,
        boolean ultimate,
        int maxLevel,
        Set<String> allowedCategories,
        double costMultiplier,
        List<String> lore,
        Map<String, Double> statsPerLevel
) {
}
