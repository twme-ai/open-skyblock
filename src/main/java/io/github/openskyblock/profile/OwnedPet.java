package io.github.openskyblock.profile;

import java.util.Locale;
import java.util.UUID;

public final class OwnedPet {
    private final String instanceId;
    private final String petId;
    private double xp;

    public OwnedPet(String instanceId, String petId, double xp) {
        this.instanceId = instanceId == null || instanceId.isBlank() ? UUID.randomUUID().toString() : instanceId;
        this.petId = petId == null ? "" : petId.toUpperCase(Locale.ROOT);
        this.xp = Math.max(0.0D, xp);
    }

    public String instanceId() {
        return instanceId;
    }

    public String petId() {
        return petId;
    }

    public double xp() {
        return xp;
    }

    public void xp(double xp) {
        this.xp = Math.max(0.0D, xp);
    }

    public void addXp(double amount) {
        xp(this.xp + amount);
    }
}
