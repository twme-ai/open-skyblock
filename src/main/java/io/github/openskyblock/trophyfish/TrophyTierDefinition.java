package io.github.openskyblock.trophyfish;

public record TrophyTierDefinition(
        TrophyFishTier tier,
        String displayName,
        double chance
) {
}
