package io.github.openskyblock.museum;

import java.util.List;

public record MuseumMilestoneDefinition(
        int level,
        double requiredSkyBlockXp,
        double bankInterestBonus,
        double bitsMultiplier,
        List<String> rewards
) {
}
