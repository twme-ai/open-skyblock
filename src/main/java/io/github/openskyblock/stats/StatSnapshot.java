package io.github.openskyblock.stats;

import java.util.Collections;
import java.util.Map;

public record StatSnapshot(Map<String, Double> stats) {
    public StatSnapshot {
        stats = Collections.unmodifiableMap(stats);
    }

    public double stat(String key) {
        return stats.getOrDefault(normalize(key), 0.0D);
    }

    public double health() {
        return stat("health");
    }

    public double defense() {
        return stat("defense");
    }

    public double damage() {
        return stat("damage");
    }

    public double strength() {
        return stat("strength");
    }

    public double critChance() {
        return stat("crit_chance");
    }

    public double critDamage() {
        return stat("crit_damage");
    }

    public double intelligence() {
        return stat("intelligence");
    }

    public double speed() {
        return stat("speed");
    }

    public double ferocity() {
        return stat("ferocity");
    }

    public static String normalize(String key) {
        return key == null ? "" : key.toLowerCase().replace('-', '_');
    }
}
