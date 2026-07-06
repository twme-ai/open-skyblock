package io.github.openskyblock.cake;

import io.github.openskyblock.profile.PlacedCake;
import io.github.openskyblock.profile.SkyBlockProfile;

public record CakePlacement(
        SkyBlockProfile profile,
        int slot,
        PlacedCake placedCake,
        CakeDefinition definition
) {
}
