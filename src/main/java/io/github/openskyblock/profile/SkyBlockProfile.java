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
    private String islandWorldName;
    private final Map<SkillType, Double> skillXp = new EnumMap<>(SkillType.class);
    private final Map<String, Long> collections = new HashMap<>();
    private final Map<String, Integer> dailyShopPurchases = new HashMap<>();
    private final Map<String, Integer> tuning = new HashMap<>();
    private final List<String> accessoryBag = new ArrayList<>();
    private final List<PlacedMinion> minions = new ArrayList<>();
    private final List<OwnedPet> pets = new ArrayList<>();
    private String shopPurchaseDay;
    private String activePetInstanceId;

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

    public String islandWorldName() {
        return islandWorldName;
    }

    public void islandWorldName(String islandWorldName) {
        this.islandWorldName = islandWorldName;
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

    public String shopPurchaseDay() {
        return shopPurchaseDay;
    }

    public void shopPurchaseDay(String shopPurchaseDay) {
        this.shopPurchaseDay = shopPurchaseDay;
    }

    public int dailyShopPurchases(String key) {
        return dailyShopPurchases.getOrDefault(key, 0);
    }

    public void setDailyShopPurchases(String key, int amount) {
        dailyShopPurchases.put(key, Math.max(0, amount));
    }

    public void addDailyShopPurchases(String key, int amount) {
        setDailyShopPurchases(key, dailyShopPurchases(key) + amount);
    }

    public void clearDailyShopPurchases() {
        dailyShopPurchases.clear();
    }

    public Map<String, Integer> dailyShopPurchases() {
        return dailyShopPurchases;
    }

    public int tuning(String stat) {
        return tuning.getOrDefault(stat, 0);
    }

    public void setTuning(String stat, int points) {
        tuning.put(stat, Math.max(0, points));
    }

    public void addTuning(String stat, int points) {
        setTuning(stat, tuning(stat) + points);
    }

    public void clearTuning() {
        tuning.clear();
    }

    public Map<String, Integer> tuning() {
        return tuning;
    }

    public List<String> accessoryBag() {
        return accessoryBag;
    }

    public boolean hasAccessory(String itemId) {
        return accessoryBag.stream().anyMatch(existing -> existing.equalsIgnoreCase(itemId));
    }

    public void addAccessory(String itemId) {
        accessoryBag.add(itemId.toUpperCase());
    }

    public boolean removeAccessory(String itemId) {
        return accessoryBag.removeIf(existing -> existing.equalsIgnoreCase(itemId));
    }

    public List<PlacedMinion> minions() {
        return minions;
    }

    public List<OwnedPet> pets() {
        return pets;
    }

    public String activePetInstanceId() {
        return activePetInstanceId;
    }

    public void activePetInstanceId(String activePetInstanceId) {
        this.activePetInstanceId = activePetInstanceId;
    }
}
