package io.github.openskyblock.dragon;

import org.bukkit.entity.EnderDragon;

public record DragonPhaseDefinition(
        String id,
        String displayName,
        double minimumHealthPercent,
        EnderDragon.Phase nativePhase,
        double outgoingDamageMultiplier,
        double cooldownMultiplier,
        boolean announce
) {
}
