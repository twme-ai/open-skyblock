package io.github.openskyblock.dungeon;

import java.util.List;

public record DungeonFloorDefinition(
        String id,
        String displayName,
        String bossName,
        int requiredCatacombsLevel,
        double baseXp,
        double completionCoins,
        List<DungeonChestDefinition> chests
) {
}
