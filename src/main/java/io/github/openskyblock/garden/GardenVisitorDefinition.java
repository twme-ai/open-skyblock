package io.github.openskyblock.garden;

import java.util.List;

public record GardenVisitorDefinition(
        String id,
        String displayName,
        int requiredGardenLevel,
        String requiredCropId,
        long requiredCropAmount,
        double gardenXp,
        long copper,
        double farmingXp,
        double coins,
        List<GardenRewardDefinition> rewards
) {
}
