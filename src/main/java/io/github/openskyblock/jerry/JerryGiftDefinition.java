package io.github.openskyblock.jerry;

import java.util.List;

public record JerryGiftDefinition(
        String id,
        String displayName,
        String itemId,
        int northStars,
        double skyBlockXp,
        List<JerryRewardDefinition> rewards
) {
}
