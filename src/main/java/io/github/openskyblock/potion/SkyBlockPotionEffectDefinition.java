package io.github.openskyblock.potion;

import java.util.Map;
import org.bukkit.potion.PotionEffectType;

public record SkyBlockPotionEffectDefinition(
        String id,
        String displayName,
        PotionEffectType type,
        int amplifier,
        boolean ambient,
        boolean particles,
        boolean icon,
        Map<String, Double> stats
) {
}
