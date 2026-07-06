package io.github.openskyblock.profile;

import java.util.Locale;
import java.util.UUID;

public final class OwnedPet {
    private final String instanceId;
    private final String petId;
    private double xp;
    private String petItemId;

    public OwnedPet(String instanceId, String petId, double xp) {
        this(instanceId, petId, xp, "");
    }

    public OwnedPet(String instanceId, String petId, double xp, String petItemId) {
        this.instanceId = instanceId == null || instanceId.isBlank() ? UUID.randomUUID().toString() : instanceId;
        this.petId = normalizeId(petId);
        this.xp = Math.max(0.0D, xp);
        this.petItemId = normalizeId(petItemId);
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

    public String petItemId() {
        return petItemId;
    }

    public void petItemId(String petItemId) {
        this.petItemId = normalizeId(petItemId);
    }

    private static String normalizeId(String id) {
        return id == null ? "" : id.toUpperCase(Locale.ROOT);
    }
}
