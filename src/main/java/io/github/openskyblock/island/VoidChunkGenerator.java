package io.github.openskyblock.island;

import java.util.Random;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

public final class VoidChunkGenerator extends ChunkGenerator {
    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        // Intentionally empty: private islands start from the platform placed by IslandService.
    }

    @Override
    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        // Keep chunks empty.
    }

    @Override
    public void generateBedrock(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        // Keep chunks empty.
    }

    @Override
    public void generateCaves(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ, @NotNull ChunkData chunkData) {
        // Keep chunks empty.
    }
}
