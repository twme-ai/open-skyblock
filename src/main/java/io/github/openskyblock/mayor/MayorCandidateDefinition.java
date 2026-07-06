package io.github.openskyblock.mayor;

import java.util.List;

public record MayorCandidateDefinition(
        String id,
        String displayName,
        String description,
        double weight,
        List<MayorPerkDefinition> perks
) {
}
