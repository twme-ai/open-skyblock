package io.github.openskyblock.dragon;

import java.util.List;

public record DragonCombatDefinition(
        double incomingDamageMultiplier,
        double outgoingDamageMultiplier,
        double cooldownMultiplier,
        double movementSpeedMultiplier,
        List<String> abilityIds,
        String deathAbilityId
) {
}
