package io.github.openskyblock.museum;

public record MuseumDonationDefinition(
        String id,
        String category,
        String displayName,
        double skyBlockXp,
        double appraisalValue
) {
}
