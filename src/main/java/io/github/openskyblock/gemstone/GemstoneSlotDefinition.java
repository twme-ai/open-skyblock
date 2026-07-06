package io.github.openskyblock.gemstone;

import java.util.Set;

public record GemstoneSlotDefinition(
        String id,
        String displayName,
        String symbol,
        Set<String> allowedGemstones
) {
}
