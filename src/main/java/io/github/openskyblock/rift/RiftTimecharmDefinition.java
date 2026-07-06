package io.github.openskyblock.rift;

import java.util.List;

public record RiftTimecharmDefinition(
        String id,
        String displayName,
        String zoneId,
        long moteCost,
        long requiredSoulExchanges,
        List<String> requiredTimecharms,
        long riftTimeBonusSeconds,
        double skyBlockXp,
        String customItemId
) {
}
