package io.github.openskyblock.pet;

import java.util.Map;

public record PetScoreReward(
        int score,
        Map<String, Double> stats
) {
}
