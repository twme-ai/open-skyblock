package io.github.openskyblock.service;

import java.util.List;

public record AbilityDefinition(String name, String type, List<String> lines, double manaCost, int cooldownSeconds) {
}
