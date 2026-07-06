package io.github.openskyblock.farmingcontest;

import java.util.List;

public record FarmingContestOccurrence(
        String id,
        long index,
        long startMillis,
        long endMillis,
        List<FarmingContestCropDefinition> crops
) {
}
