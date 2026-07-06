package io.github.openskyblock.service;

import java.util.List;
import java.util.Map;

public record AbilityDefinition(
        String name,
        String type,
        String action,
        List<String> lines,
        double manaCost,
        int cooldownSeconds,
        Map<String, Double> parameters
) {
    public double parameter(String key, double fallback) {
        return parameters.getOrDefault(key, fallback);
    }
}
