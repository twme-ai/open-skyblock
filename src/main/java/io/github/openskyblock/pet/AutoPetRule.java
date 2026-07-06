package io.github.openskyblock.pet;

import java.util.Locale;

public record AutoPetRule(AutoPetTrigger trigger, String petId) {
    public AutoPetRule {
        if (trigger == null) {
            throw new IllegalArgumentException("trigger cannot be null");
        }
        petId = petId == null ? "" : petId.toUpperCase(Locale.ROOT);
    }
}
