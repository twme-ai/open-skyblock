package io.github.openskyblock.profile;

import io.github.openskyblock.pet.AutoPetRule;
import io.github.openskyblock.service.SkillType;
import io.github.openskyblock.wardrobe.WardrobeSet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.inventory.ItemStack;

public final class SkyBlockProfile {
    private final UUID uniqueId;
    private String playerName;
    private double purse;
    private double bank;
    private long bankInterestLastMillis = System.currentTimeMillis();
    private String islandWorldName;
    private final Map<SkillType, Double> skillXp = new EnumMap<>(SkillType.class);
    private final Map<String, Long> collections = new HashMap<>();
    private final Map<String, Map<String, Long>> trophyFish = new HashMap<>();
    private final Map<String, Integer> dailyShopPurchases = new HashMap<>();
    private final Map<String, Integer> tuning = new HashMap<>();
    private final Map<String, ItemStack> equipment = new HashMap<>();
    private final Map<Integer, WardrobeSet> wardrobe = new HashMap<>();
    private final Map<Integer, ItemStack[]> storagePages = new HashMap<>();
    private final Map<Integer, OwnedBackpack> backpacks = new HashMap<>();
    private final Map<String, Map<String, Long>> sacks = new HashMap<>();
    private final Map<String, Long> quiver = new HashMap<>();
    private final Map<String, Long> potionEffects = new HashMap<>();
    private final Map<String, Long> cakeBuffs = new HashMap<>();
    private final Map<String, Integer> upgrades = new HashMap<>();
    private final Map<String, Double> essence = new HashMap<>();
    private final Map<String, Long> bestiaryKills = new HashMap<>();
    private final Map<String, Integer> bestiaryTiers = new HashMap<>();
    private final Map<String, Long> museumDonations = new HashMap<>();
    private final Map<String, Integer> darkAuctionPurchases = new HashMap<>();
    private final Map<String, String> mayorVotes = new HashMap<>();
    private final Map<String, Long> farmingContestMedals = new HashMap<>();
    private final Map<String, Map<String, Long>> farmingContestScores = new HashMap<>();
    private final Map<String, String> farmingContestRewards = new HashMap<>();
    private final Map<Integer, ActiveCommission> activeCommissions = new HashMap<>();
    private final Map<String, Double> hotmPowder = new HashMap<>();
    private final Map<String, Double> slayerXp = new HashMap<>();
    private final Map<String, Integer> slayerLevels = new HashMap<>();
    private final List<String> accessoryBag = new ArrayList<>();
    private final List<PlacedMinion> minions = new ArrayList<>();
    private final List<PlacedCake> placedCakes = new ArrayList<>();
    private final List<ItemStack> darkAuctionClaims = new ArrayList<>();
    private final List<OwnedPet> pets = new ArrayList<>();
    private final List<AutoPetRule> autoPetRules = new ArrayList<>();
    private String shopPurchaseDay;
    private String activePetInstanceId;
    private String selectedQuiverItem;
    private ActiveSlayerQuest activeSlayer;
    private long jacobsTickets;
    private double hotmXp;
    private long totalCommissions;
    private String commissionDay;
    private int dailyCommissions;
    private long cookieBuffExpiresAtMillis;
    private long bits;
    private long bitsAvailable;
    private double fameXp;
    private long cookiesConsumed;
    private long bitsLastAccrualMillis;

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

    public long bankInterestLastMillis() {
        return bankInterestLastMillis;
    }

    public void bankInterestLastMillis(long bankInterestLastMillis) {
        this.bankInterestLastMillis = Math.max(0L, bankInterestLastMillis);
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

    public long trophyFish(String fishId, String tier) {
        Map<String, Long> tiers = trophyFish.get(fishId.toUpperCase());
        return tiers == null ? 0L : tiers.getOrDefault(tier.toUpperCase(), 0L);
    }

    public void setTrophyFish(String fishId, String tier, long amount) {
        String normalizedFish = fishId.toUpperCase();
        String normalizedTier = tier.toUpperCase();
        if (amount <= 0L) {
            Map<String, Long> tiers = trophyFish.get(normalizedFish);
            if (tiers != null) {
                tiers.remove(normalizedTier);
                if (tiers.isEmpty()) {
                    trophyFish.remove(normalizedFish);
                }
            }
            return;
        }
        trophyFish.computeIfAbsent(normalizedFish, ignored -> new HashMap<>()).put(normalizedTier, amount);
    }

    public void addTrophyFish(String fishId, String tier, long amount) {
        if (amount <= 0L) {
            return;
        }
        setTrophyFish(fishId, tier, trophyFish(fishId, tier) + amount);
    }

    public long trophyFishTotal(String fishId) {
        Map<String, Long> tiers = trophyFish.get(fishId.toUpperCase());
        if (tiers == null) {
            return 0L;
        }
        return tiers.values().stream().mapToLong(Long::longValue).sum();
    }

    public long trophyFishTotal() {
        return trophyFish.values().stream()
                .flatMap(map -> map.values().stream())
                .mapToLong(Long::longValue)
                .sum();
    }

    public Map<String, Map<String, Long>> trophyFish() {
        return trophyFish;
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

    public Map<String, ItemStack> equipment() {
        return equipment;
    }

    public Map<Integer, WardrobeSet> wardrobe() {
        return wardrobe;
    }

    public Map<Integer, ItemStack[]> storagePages() {
        return storagePages;
    }

    public Map<Integer, OwnedBackpack> backpacks() {
        return backpacks;
    }

    public Map<String, Map<String, Long>> sacks() {
        return sacks;
    }

    public Map<String, Long> quiver() {
        return quiver;
    }

    public String selectedQuiverItem() {
        return selectedQuiverItem;
    }

    public void selectedQuiverItem(String selectedQuiverItem) {
        this.selectedQuiverItem = selectedQuiverItem;
    }

    public Map<String, Long> potionEffects() {
        return potionEffects;
    }

    public Map<String, Long> cakeBuffs() {
        return cakeBuffs;
    }

    public Map<String, Integer> upgrades() {
        return upgrades;
    }

    public double essence(String essenceId) {
        return essence.getOrDefault(essenceId.toUpperCase(), 0.0D);
    }

    public void setEssence(String essenceId, double amount) {
        if (amount <= 0.0D) {
            essence.remove(essenceId.toUpperCase());
            return;
        }
        essence.put(essenceId.toUpperCase(), amount);
    }

    public void addEssence(String essenceId, double amount) {
        setEssence(essenceId, essence(essenceId) + amount);
    }

    public Map<String, Double> essence() {
        return essence;
    }

    public Map<String, Long> bestiaryKills() {
        return bestiaryKills;
    }

    public Map<String, Integer> bestiaryTiers() {
        return bestiaryTiers;
    }

    public Map<String, Long> museumDonations() {
        return museumDonations;
    }

    public boolean hasMuseumDonation(String donationId) {
        return museumDonations.containsKey(donationId.toUpperCase());
    }

    public void addMuseumDonation(String donationId, long donatedAtMillis) {
        museumDonations.put(donationId.toUpperCase(), Math.max(0L, donatedAtMillis));
    }

    public Map<String, Integer> darkAuctionPurchases() {
        return darkAuctionPurchases;
    }

    public int darkAuctionPurchases(String itemId) {
        return darkAuctionPurchases.getOrDefault(itemId.toUpperCase(), 0);
    }

    public void setDarkAuctionPurchases(String itemId, int amount) {
        if (amount <= 0) {
            darkAuctionPurchases.remove(itemId.toUpperCase());
            return;
        }
        darkAuctionPurchases.put(itemId.toUpperCase(), amount);
    }

    public void addDarkAuctionPurchase(String itemId, int amount) {
        setDarkAuctionPurchases(itemId, darkAuctionPurchases(itemId) + amount);
    }

    public Map<String, String> mayorVotes() {
        return mayorVotes;
    }

    public String mayorVote(String electionId) {
        return mayorVotes.get(electionId.toUpperCase());
    }

    public void setMayorVote(String electionId, String candidateId) {
        if (candidateId == null || candidateId.isBlank()) {
            mayorVotes.remove(electionId.toUpperCase());
            return;
        }
        mayorVotes.put(electionId.toUpperCase(), candidateId.toUpperCase());
    }

    public long jacobsTickets() {
        return jacobsTickets;
    }

    public void jacobsTickets(long jacobsTickets) {
        this.jacobsTickets = Math.max(0L, jacobsTickets);
    }

    public void addJacobsTickets(long amount) {
        jacobsTickets(jacobsTickets + amount);
    }

    public long farmingContestMedal(String medalId) {
        return farmingContestMedals.getOrDefault(medalId.toUpperCase(), 0L);
    }

    public void setFarmingContestMedal(String medalId, long amount) {
        if (amount <= 0L) {
            farmingContestMedals.remove(medalId.toUpperCase());
            return;
        }
        farmingContestMedals.put(medalId.toUpperCase(), amount);
    }

    public void addFarmingContestMedal(String medalId, long amount) {
        setFarmingContestMedal(medalId, farmingContestMedal(medalId) + amount);
    }

    public Map<String, Long> farmingContestMedals() {
        return farmingContestMedals;
    }

    public long farmingContestScore(String contestId, String cropId) {
        Map<String, Long> scores = farmingContestScores.get(contestId.toUpperCase());
        return scores == null ? 0L : scores.getOrDefault(cropId.toUpperCase(), 0L);
    }

    public void addFarmingContestScore(String contestId, String cropId, long amount) {
        if (amount <= 0L) {
            return;
        }
        Map<String, Long> scores = farmingContestScores.computeIfAbsent(contestId.toUpperCase(), ignored -> new HashMap<>());
        String crop = cropId.toUpperCase();
        scores.put(crop, scores.getOrDefault(crop, 0L) + amount);
    }

    public Map<String, Map<String, Long>> farmingContestScores() {
        return farmingContestScores;
    }

    public Map<String, String> farmingContestRewards() {
        return farmingContestRewards;
    }

    public boolean hasFarmingContestReward(String contestId) {
        return farmingContestRewards.containsKey(contestId.toUpperCase());
    }

    public void setFarmingContestReward(String contestId, String rewardId) {
        farmingContestRewards.put(contestId.toUpperCase(), rewardId == null || rewardId.isBlank() ? "NONE" : rewardId.toUpperCase());
    }

    public double hotmXp() {
        return hotmXp;
    }

    public void hotmXp(double hotmXp) {
        this.hotmXp = Math.max(0.0D, hotmXp);
    }

    public void addHotmXp(double amount) {
        hotmXp(hotmXp + amount);
    }

    public double hotmPowder(String powderId) {
        return hotmPowder.getOrDefault(powderId.toUpperCase(), 0.0D);
    }

    public void setHotmPowder(String powderId, double amount) {
        if (amount <= 0.0D) {
            hotmPowder.remove(powderId.toUpperCase());
            return;
        }
        hotmPowder.put(powderId.toUpperCase(), amount);
    }

    public void addHotmPowder(String powderId, double amount) {
        setHotmPowder(powderId, hotmPowder(powderId) + amount);
    }

    public Map<String, Double> hotmPowder() {
        return hotmPowder;
    }

    public Map<Integer, ActiveCommission> activeCommissions() {
        return activeCommissions;
    }

    public long totalCommissions() {
        return totalCommissions;
    }

    public void totalCommissions(long totalCommissions) {
        this.totalCommissions = Math.max(0L, totalCommissions);
    }

    public void addTotalCommissions(long amount) {
        totalCommissions(totalCommissions + amount);
    }

    public String commissionDay() {
        return commissionDay;
    }

    public void commissionDay(String commissionDay) {
        this.commissionDay = commissionDay;
    }

    public int dailyCommissions() {
        return dailyCommissions;
    }

    public void dailyCommissions(int dailyCommissions) {
        this.dailyCommissions = Math.max(0, dailyCommissions);
    }

    public void addDailyCommission() {
        dailyCommissions(dailyCommissions + 1);
    }

    public long cookieBuffExpiresAtMillis() {
        return cookieBuffExpiresAtMillis;
    }

    public void cookieBuffExpiresAtMillis(long cookieBuffExpiresAtMillis) {
        this.cookieBuffExpiresAtMillis = Math.max(0L, cookieBuffExpiresAtMillis);
    }

    public long bits() {
        return bits;
    }

    public void bits(long bits) {
        this.bits = Math.max(0L, bits);
    }

    public void addBits(long amount) {
        bits(bits + amount);
    }

    public long bitsAvailable() {
        return bitsAvailable;
    }

    public void bitsAvailable(long bitsAvailable) {
        this.bitsAvailable = Math.max(0L, bitsAvailable);
    }

    public void addBitsAvailable(long amount) {
        bitsAvailable(bitsAvailable + amount);
    }

    public double fameXp() {
        return fameXp;
    }

    public void fameXp(double fameXp) {
        this.fameXp = Math.max(0.0D, fameXp);
    }

    public void addFameXp(double amount) {
        fameXp(fameXp + amount);
    }

    public long cookiesConsumed() {
        return cookiesConsumed;
    }

    public void cookiesConsumed(long cookiesConsumed) {
        this.cookiesConsumed = Math.max(0L, cookiesConsumed);
    }

    public void addCookieConsumed() {
        cookiesConsumed(cookiesConsumed + 1L);
    }

    public long bitsLastAccrualMillis() {
        return bitsLastAccrualMillis;
    }

    public void bitsLastAccrualMillis(long bitsLastAccrualMillis) {
        this.bitsLastAccrualMillis = Math.max(0L, bitsLastAccrualMillis);
    }

    public Map<String, Double> slayerXp() {
        return slayerXp;
    }

    public Map<String, Integer> slayerLevels() {
        return slayerLevels;
    }

    public ActiveSlayerQuest activeSlayer() {
        return activeSlayer;
    }

    public void activeSlayer(ActiveSlayerQuest activeSlayer) {
        this.activeSlayer = activeSlayer;
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

    public List<PlacedCake> placedCakes() {
        return placedCakes;
    }

    public List<ItemStack> darkAuctionClaims() {
        return darkAuctionClaims;
    }

    public List<OwnedPet> pets() {
        return pets;
    }

    public List<AutoPetRule> autoPetRules() {
        return autoPetRules;
    }

    public String activePetInstanceId() {
        return activePetInstanceId;
    }

    public void activePetInstanceId(String activePetInstanceId) {
        this.activePetInstanceId = activePetInstanceId;
    }
}
