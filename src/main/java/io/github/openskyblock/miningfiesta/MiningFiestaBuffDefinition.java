package io.github.openskyblock.miningfiesta;

import java.util.Map;

public record MiningFiestaBuffDefinition(
        String id,
        String displayName,
        long durationSeconds,
        Map<String, Integer> requiredItems,
        Map<String, Double> stats
) {
}
