package io.github.openskyblock.upgrade;

import java.util.Map;

public record UpgradeTierDefinition(
        int level,
        double cost,
        Map<String, Double> stats,
        Map<String, Integer> capacities
) {
}
