package io.github.openskyblock.rift;

import java.util.List;

public record RiftZoneDefinition(
        String id,
        String displayName,
        int requiredTimecharmCount,
        List<String> requiredTimecharms,
        long entryCostSeconds
) {
}
