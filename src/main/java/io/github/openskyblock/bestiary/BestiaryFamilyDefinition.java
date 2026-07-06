package io.github.openskyblock.bestiary;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record BestiaryFamilyDefinition(
        String id,
        String displayName,
        String category,
        Set<String> mobIds,
        Map<Integer, BestiaryTierDefinition> tiers
) {
    public List<Integer> sortedTierNumbers() {
        return tiers.keySet().stream().sorted().toList();
    }
}
