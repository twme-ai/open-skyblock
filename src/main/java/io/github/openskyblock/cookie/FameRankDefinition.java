package io.github.openskyblock.cookie;

public record FameRankDefinition(
        String id,
        String displayName,
        double requiredFame,
        double bitsMultiplier
) {
}
