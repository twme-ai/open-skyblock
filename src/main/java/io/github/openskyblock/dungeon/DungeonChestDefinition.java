package io.github.openskyblock.dungeon;

import java.util.List;

public record DungeonChestDefinition(
        String id,
        String displayName,
        int minScore,
        double cost,
        int rewardRolls,
        List<DungeonRewardDefinition> rewards
) {
}
