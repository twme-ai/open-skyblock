package io.github.openskyblock.forge;

import java.util.List;

public record ForgeRecipeDefinition(
        String id,
        String displayName,
        String description,
        int requiredHotmLevel,
        long durationMillis,
        double cost,
        List<ForgeItemDefinition> ingredients,
        ForgeItemDefinition output
) {
}
