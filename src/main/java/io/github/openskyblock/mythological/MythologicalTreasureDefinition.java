package io.github.openskyblock.mythological;

import io.github.openskyblock.service.Rarity;
import java.util.List;

public record MythologicalTreasureDefinition(
        String id,
        String displayName,
        Rarity minimumRarity,
        double weight,
        double coins,
        List<MythologicalRewardDefinition> rewards
) {
}
