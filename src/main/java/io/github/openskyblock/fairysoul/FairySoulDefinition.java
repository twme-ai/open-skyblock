package io.github.openskyblock.fairysoul;

public record FairySoulDefinition(
        String id,
        String displayName,
        String worldName,
        int x,
        int y,
        int z,
        double claimRadius
) {
}
