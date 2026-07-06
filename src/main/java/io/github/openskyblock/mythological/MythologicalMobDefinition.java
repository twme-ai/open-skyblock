package io.github.openskyblock.mythological;

import io.github.openskyblock.service.Rarity;
import java.util.List;

public record MythologicalMobDefinition(
        String id,
        String displayName,
        Rarity minimumRarity,
        double weight,
        double combatXp,
        double coins,
        List<MythologicalRewardDefinition> rewards
) {
}
