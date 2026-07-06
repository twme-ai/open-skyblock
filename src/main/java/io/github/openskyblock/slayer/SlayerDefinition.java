package io.github.openskyblock.slayer;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record SlayerDefinition(
        String id,
        String displayName,
        String bossName,
        Set<String> mobIds,
        Map<Integer, SlayerTierDefinition> tiers
) {
    public List<Integer> sortedTierNumbers() {
        return tiers.keySet().stream().sorted().toList();
    }
}
