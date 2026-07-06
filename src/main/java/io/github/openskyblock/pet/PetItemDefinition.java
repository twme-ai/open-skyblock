package io.github.openskyblock.pet;

import java.util.Map;

public record PetItemDefinition(
        String id,
        String displayName,
        String itemId,
        Map<String, Double> stats
) {
}
