package io.github.openskyblock.dragon;

import java.util.Set;
import org.bukkit.Particle;
import org.bukkit.entity.EnderDragon;

public record DragonAbilityDefinition(
        String id,
        String displayName,
        DragonAbilityType type,
        long initialDelayMillis,
        long cooldownMillis,
        long durationMillis,
        double damage,
        double radius,
        double range,
        double speed,
        int targetCount,
        Set<String> allowedPhases,
        EnderDragon.Phase nativePhase,
        Particle particle,
        String sound,
        boolean announce
) {
    public boolean availableIn(String phaseId) {
        return allowedPhases.isEmpty() || allowedPhases.contains(phaseId);
    }
}
