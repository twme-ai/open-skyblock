package io.github.openskyblock.dragon;

import java.util.List;

public record DragonDefinition(
        String id,
        String displayName,
        double spawnWeight,
        double health,
        double combatXp,
        String fragmentItemId,
        int baseFragments,
        int fragmentsPerEye,
        int fragmentsPerRank,
        DragonCombatDefinition combat,
        List<DragonRewardDefinition> rewards
) {
}
