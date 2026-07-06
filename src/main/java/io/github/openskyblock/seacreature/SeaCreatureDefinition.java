package io.github.openskyblock.seacreature;

public record SeaCreatureDefinition(
        String id,
        String displayName,
        String mobId,
        int requiredFishingLevel,
        double weight
) {
}
