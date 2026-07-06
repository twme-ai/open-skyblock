package io.github.openskyblock.spooky;

import java.util.List;

public record SpookyMobDefinition(
        String id,
        String displayName,
        int minimumCombatLevel,
        int greenCandy,
        int purpleCandy,
        double combatXp,
        double coins,
        double weight,
        List<SpookyRewardDefinition> rewards
) {
}
