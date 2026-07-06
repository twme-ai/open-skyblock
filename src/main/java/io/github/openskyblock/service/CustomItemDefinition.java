package io.github.openskyblock.service;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;

public record CustomItemDefinition(
        String id,
        Material material,
        String displayName,
        String category,
        String armorSet,
        Rarity rarity,
        List<String> lore,
        Map<String, Double> stats,
        AbilityDefinition ability
) {
    public double stat(String stat) {
        return stats.getOrDefault(stat, 0.0D);
    }
}
