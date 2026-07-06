package io.github.openskyblock.faction;

import java.util.List;

public record FactionQuestDefinition(
        String id,
        String displayName,
        String tier,
        long reputation,
        double combatXp,
        double coins,
        long requiredReputation,
        List<FactionRewardDefinition> rewards
) {
}
