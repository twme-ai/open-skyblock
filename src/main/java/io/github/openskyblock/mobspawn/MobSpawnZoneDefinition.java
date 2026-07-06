package io.github.openskyblock.mobspawn;

import java.util.List;

public record MobSpawnZoneDefinition(
        String id,
        String displayName,
        String worldName,
        double centerX,
        double centerY,
        double centerZ,
        double radiusX,
        double radiusY,
        double radiusZ,
        boolean useHighestBlock,
        boolean loadChunks,
        double activationRadius,
        int minPlayers,
        int maxAlive,
        int batchSize,
        long intervalTicks,
        List<MobSpawnEntry> mobs
) {
}
