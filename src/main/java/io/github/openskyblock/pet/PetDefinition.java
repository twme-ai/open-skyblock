package io.github.openskyblock.pet;

import io.github.openskyblock.service.Rarity;
import java.util.List;
import java.util.Map;
import org.bukkit.Material;

public record PetDefinition(
        String id,
        Material material,
        String displayName,
        Rarity rarity,
        int maxLevel,
        double xpPerLevel,
        List<String> lore,
        Map<String, Double> baseStats,
        Map<String, Double> statsPerLevel
) {
}
