package io.github.openskyblock.service;

import java.util.List;
import java.util.Map;
import org.bukkit.Material;

public record CollectionDefinition(String id, String displayName, Material material, Map<Integer, CollectionTier> tiers) {
    public List<Integer> sortedTierNumbers() {
        return tiers.keySet().stream().sorted().toList();
    }
}
