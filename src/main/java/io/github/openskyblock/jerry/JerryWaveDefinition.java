package io.github.openskyblock.jerry;

import java.util.List;

public record JerryWaveDefinition(
        int wave,
        String displayName,
        double combatXp,
        double coins,
        long northStars,
        double skyBlockXp,
        List<JerryRewardDefinition> rewards
) {
}
