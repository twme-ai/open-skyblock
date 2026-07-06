package io.github.openskyblock.dojo;

public record DojoBeltDefinition(
        String id,
        String displayName,
        int requiredPoints,
        String itemId,
        double skyBlockXp
) {
}
