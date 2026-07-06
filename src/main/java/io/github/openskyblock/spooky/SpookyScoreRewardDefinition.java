package io.github.openskyblock.spooky;

import java.util.List;

public record SpookyScoreRewardDefinition(
        String id,
        String displayName,
        int requiredScore,
        double skyBlockXp,
        List<SpookyRewardDefinition> rewards
) {
}
