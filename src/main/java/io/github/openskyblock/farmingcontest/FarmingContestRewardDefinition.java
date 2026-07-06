package io.github.openskyblock.farmingcontest;

public record FarmingContestRewardDefinition(
        String id,
        String displayName,
        double percentile,
        String medalId,
        long medalAmount,
        long tickets
) {
}
