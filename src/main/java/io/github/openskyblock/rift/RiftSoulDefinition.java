package io.github.openskyblock.rift;

import java.util.List;

public record RiftSoulDefinition(
        String id,
        String displayName,
        String zoneId,
        int requiredTimecharmCount,
        List<String> requiredTimecharms,
        long moteReward
) {
}
