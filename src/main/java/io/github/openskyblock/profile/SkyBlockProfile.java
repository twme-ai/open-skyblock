package io.github.openskyblock.profile;

import io.github.openskyblock.service.SkillType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SkyBlockProfile {
    private final UUID uniqueId;
    private String playerName;
    private double purse;
    private double bank;
    private final Map<SkillType, Double> skillXp = new EnumMap<>(SkillType.class);
    private final Map<String, Long> collections = new HashMap<>();
    private final List<PlacedMinion> minions = new ArrayList<>();

    public SkyBlockProfile(UUID uniqueId, String playerName, double purse, double bank) {
        this.uniqueId = uniqueId;
        this.playerName = playerName;
        this.purse = purse;
        this.bank = bank;
    }

    public UUID uniqueId() {
        return uniqueId;
    }

    public String playerName() {
        return playerName;
    }

    public void playerName(String playerName) {
        this.playerName = playerName;
    }

    public double purse() {
        return purse;
    }

    public void purse(double purse) {
        this.purse = Math.max(0.0D, purse);
    }

    public double bank() {
        return bank;
    }

    public void bank(double bank) {
        this.bank = Math.max(0.0D, bank);
    }

    public double skillXp(SkillType skillType) {
        return skillXp.getOrDefault(skillType, 0.0D);
    }

    public void setSkillXp(SkillType skillType, double xp) {
        skillXp.put(skillType, Math.max(0.0D, xp));
    }

    public void addSkillXp(SkillType skillType, double xp) {
        setSkillXp(skillType, skillXp(skillType) + xp);
    }

    public Map<SkillType, Double> skillXp() {
        return skillXp;
    }

    public long collectionAmount(String collectionId) {
        return collections.getOrDefault(collectionId, 0L);
    }

    public void setCollectionAmount(String collectionId, long amount) {
        collections.put(collectionId, Math.max(0L, amount));
    }

    public void addCollectionAmount(String collectionId, long amount) {
        setCollectionAmount(collectionId, collectionAmount(collectionId) + amount);
    }

    public Map<String, Long> collections() {
        return collections;
    }

    public List<PlacedMinion> minions() {
        return minions;
    }
}
