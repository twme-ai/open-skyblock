package io.github.openskyblock.service;

import io.github.openskyblock.profile.PlacedMinion;
import io.github.openskyblock.profile.SkyBlockProfile;

public record MinionPlacement(SkyBlockProfile profile, int slot, PlacedMinion placedMinion, MinionDefinition definition) {
}
