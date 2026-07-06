package io.github.openskyblock.stats;

import java.util.List;
import java.util.Map;

public record ArmorSetDefinition(String id, String displayName, int requiredPieces, List<String> lore, Map<String, Double> stats) {
}
