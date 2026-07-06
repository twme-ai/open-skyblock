package io.github.openskyblock.gemstone;

import java.util.Map;

public record GemstoneDefinition(
        String id,
        String displayName,
        Map<String, Map<String, Double>> statsByTier
) {
}
