package io.github.openskyblock.mayor;

import java.util.Map;

public record MayorPerkDefinition(
        String id,
        String displayName,
        String description,
        Map<String, Double> modifiers
) {
}
