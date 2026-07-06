package io.github.openskyblock.profile;

import io.github.openskyblock.pet.AutoPetRule;
import io.github.openskyblock.service.SkillType;
import io.github.openskyblock.wardrobe.WardrobeSet;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final Set<String> fairySouls = new HashSet<>();
    private final Map<String, Map<String, Long>> trophyFish = new HashMap<>();
    private final Map<String, Integer> experimentCompletions = new HashMap<>();
    private final Map<String, Integer> dungeonCompletions = new HashMap<>();
    private final Map<String, Double> dungeonClassXp = new HashMap<>();
    private final Map<String, Long> gardenCropHarvests = new HashMap<>();
    private final Map<String, Long> gardenCropStorage = new HashMap<>();
    private final Set<String> gardenPlots = new HashSet<>();
    private final Map<String, Integer> gardenVisitorsServed = new HashMap<>();
    private final Map<String, Integer> dragonKills = new HashMap<>();
    private final Set<String> riftTimecharms = new HashSet<>();
    private final Set<String> riftSouls = new HashSet<>();
    private final Map<String, Integer> kuudraCompletions = new HashMap<>();
    private final Map<String, Long> factionReputation = new HashMap<>();
    private final Map<String, Integer> factionQuestCompletions = new HashMap<>();
    private final Map<String, Integer> factionMinibossKills = new HashMap<>();
    private final Map<String, Integer> dojoChallengeScores = new HashMap<>();
    private final Set<String> claimedDojoBelts = new HashSet<>();
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
    private final Map<Integer, ActiveForgeJob> forgeJobs = new HashMap<>();
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
    private String experimentDay;
    private int experimentBonusClicks;
    private String selectedDungeonClass;
    private String dungeonRunDay;
    private int dailyDungeonRuns;
    private double gardenXp;
    private long gardenCopper;
    private long gardenCompost;
    private long gardenVisitorOffers;
    private int placedDragonEyes;
    private long summoningEyesUsed;
    private double bestDragonDamage;
    private long riftMotes;
    private long riftSoulExchanges;
    private long riftEntries;
    private long riftTimeSpentSeconds;
    private long riftOrbsCollected;
    private long kuudraTeeth;
    private long kuudraKeysCrafted;
    private long kuudraKeysUsed;
    private int bestKuudraScore;
    private String selectedFaction;
    private String factionDay;
    private int dailyFactionQuests;
    private int dailyFactionMinibosses;
    private String activePetInstanceId;
    private String selectedQuiverItem;
    private ActiveSlayerQuest activeSlayer;
    private long jacobsTickets;
    private double hotmXp;
    private long totalCommissions;
    private String commissionDay;
    private int dailyCommissions;
    private long fairySoulExchanges;
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

    public boolean hasFairySoul(String soulId) {
        return fairySouls.contains(soulId.toUpperCase());
    }

    public boolean addFairySoul(String soulId) {
        return fairySouls.add(soulId.toUpperCase());
    }

    public Set<String> fairySouls() {
        return fairySouls;
    }

    public long fairySoulExchanges() {
        return fairySoulExchanges;
    }

    public void fairySoulExchanges(long fairySoulExchanges) {
        this.fairySoulExchanges = Math.max(0L, fairySoulExchanges);
    }

    public void addFairySoulExchanges(long amount) {
        fairySoulExchanges(fairySoulExchanges + amount);
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

    public String experimentDay() {
        return experimentDay;
    }

    public void experimentDay(String experimentDay) {
        this.experimentDay = experimentDay;
    }

    public int experimentCompletions(String experimentId) {
        return experimentCompletions.getOrDefault(experimentId.toUpperCase(), 0);
    }

    public void setExperimentCompletions(String experimentId, int amount) {
        String normalized = experimentId.toUpperCase();
        if (amount <= 0) {
            experimentCompletions.remove(normalized);
            return;
        }
        experimentCompletions.put(normalized, amount);
    }

    public void addExperimentCompletion(String experimentId, int amount) {
        setExperimentCompletions(experimentId, experimentCompletions(experimentId) + amount);
    }

    public void clearExperimentCompletions() {
        experimentCompletions.clear();
    }

    public Map<String, Integer> experimentCompletions() {
        return experimentCompletions;
    }

    public int experimentBonusClicks() {
        return experimentBonusClicks;
    }

    public void experimentBonusClicks(int experimentBonusClicks) {
        this.experimentBonusClicks = Math.max(0, experimentBonusClicks);
    }

    public void addExperimentBonusClicks(int amount) {
        experimentBonusClicks(experimentBonusClicks + amount);
    }

    public int dungeonCompletions(String floorId) {
        return dungeonCompletions.getOrDefault(floorId.toUpperCase(), 0);
    }

    public void setDungeonCompletions(String floorId, int amount) {
        String normalized = floorId.toUpperCase();
        if (amount <= 0) {
            dungeonCompletions.remove(normalized);
            return;
        }
        dungeonCompletions.put(normalized, amount);
    }

    public void addDungeonCompletion(String floorId, int amount) {
        setDungeonCompletions(floorId, dungeonCompletions(floorId) + amount);
    }

    public Map<String, Integer> dungeonCompletions() {
        return dungeonCompletions;
    }

    public double dungeonClassXp(String classId) {
        return dungeonClassXp.getOrDefault(classId.toUpperCase(), 0.0D);
    }

    public void setDungeonClassXp(String classId, double xp) {
        String normalized = classId.toUpperCase();
        if (xp <= 0.0D) {
            dungeonClassXp.remove(normalized);
            return;
        }
        dungeonClassXp.put(normalized, xp);
    }

    public void addDungeonClassXp(String classId, double xp) {
        setDungeonClassXp(classId, dungeonClassXp(classId) + xp);
    }

    public Map<String, Double> dungeonClassXp() {
        return dungeonClassXp;
    }

    public String selectedDungeonClass() {
        return selectedDungeonClass;
    }

    public void selectedDungeonClass(String selectedDungeonClass) {
        this.selectedDungeonClass = selectedDungeonClass == null ? null : selectedDungeonClass.toUpperCase();
    }

    public String dungeonRunDay() {
        return dungeonRunDay;
    }

    public void dungeonRunDay(String dungeonRunDay) {
        this.dungeonRunDay = dungeonRunDay;
    }

    public int dailyDungeonRuns() {
        return dailyDungeonRuns;
    }

    public void dailyDungeonRuns(int dailyDungeonRuns) {
        this.dailyDungeonRuns = Math.max(0, dailyDungeonRuns);
    }

    public void addDailyDungeonRun() {
        dailyDungeonRuns(dailyDungeonRuns + 1);
    }

    public double gardenXp() {
        return gardenXp;
    }

    public void gardenXp(double gardenXp) {
        this.gardenXp = Math.max(0.0D, gardenXp);
    }

    public void addGardenXp(double amount) {
        gardenXp(gardenXp + amount);
    }

    public long gardenCopper() {
        return gardenCopper;
    }

    public void gardenCopper(long gardenCopper) {
        this.gardenCopper = Math.max(0L, gardenCopper);
    }

    public void addGardenCopper(long amount) {
        gardenCopper(gardenCopper + amount);
    }

    public long gardenCompost() {
        return gardenCompost;
    }

    public void gardenCompost(long gardenCompost) {
        this.gardenCompost = Math.max(0L, gardenCompost);
    }

    public void addGardenCompost(long amount) {
        gardenCompost(gardenCompost + amount);
    }

    public long gardenCropHarvests(String cropId) {
        return gardenCropHarvests.getOrDefault(cropId.toUpperCase(), 0L);
    }

    public void setGardenCropHarvests(String cropId, long amount) {
        String normalized = cropId.toUpperCase();
        if (amount <= 0L) {
            gardenCropHarvests.remove(normalized);
            return;
        }
        gardenCropHarvests.put(normalized, amount);
    }

    public void addGardenCropHarvests(String cropId, long amount) {
        setGardenCropHarvests(cropId, gardenCropHarvests(cropId) + amount);
    }

    public Map<String, Long> gardenCropHarvests() {
        return gardenCropHarvests;
    }

    public long gardenCropStorage(String cropId) {
        return gardenCropStorage.getOrDefault(cropId.toUpperCase(), 0L);
    }

    public void setGardenCropStorage(String cropId, long amount) {
        String normalized = cropId.toUpperCase();
        if (amount <= 0L) {
            gardenCropStorage.remove(normalized);
            return;
        }
        gardenCropStorage.put(normalized, amount);
    }

    public void addGardenCropStorage(String cropId, long amount) {
        setGardenCropStorage(cropId, gardenCropStorage(cropId) + amount);
    }

    public Map<String, Long> gardenCropStorage() {
        return gardenCropStorage;
    }

    public boolean hasGardenPlot(String plotId) {
        return gardenPlots.contains(plotId.toUpperCase());
    }

    public boolean addGardenPlot(String plotId) {
        return gardenPlots.add(plotId.toUpperCase());
    }

    public Set<String> gardenPlots() {
        return gardenPlots;
    }

    public int gardenVisitorServed(String visitorId) {
        return gardenVisitorsServed.getOrDefault(visitorId.toUpperCase(), 0);
    }

    public void setGardenVisitorServed(String visitorId, int amount) {
        String normalized = visitorId.toUpperCase();
        if (amount <= 0) {
            gardenVisitorsServed.remove(normalized);
            return;
        }
        gardenVisitorsServed.put(normalized, amount);
    }

    public void addGardenVisitorServed(String visitorId, int amount) {
        setGardenVisitorServed(visitorId, gardenVisitorServed(visitorId) + amount);
        gardenVisitorOffers(gardenVisitorOffers + Math.max(0, amount));
    }

    public Map<String, Integer> gardenVisitorsServed() {
        return gardenVisitorsServed;
    }

    public long gardenVisitorOffers() {
        return gardenVisitorOffers;
    }

    public void gardenVisitorOffers(long gardenVisitorOffers) {
        this.gardenVisitorOffers = Math.max(0L, gardenVisitorOffers);
    }

    public int placedDragonEyes() {
        return placedDragonEyes;
    }

    public void placedDragonEyes(int placedDragonEyes) {
        this.placedDragonEyes = Math.max(0, placedDragonEyes);
    }

    public long summoningEyesUsed() {
        return summoningEyesUsed;
    }

    public void summoningEyesUsed(long summoningEyesUsed) {
        this.summoningEyesUsed = Math.max(0L, summoningEyesUsed);
    }

    public void addSummoningEyesUsed(long amount) {
        summoningEyesUsed(summoningEyesUsed + amount);
    }

    public double bestDragonDamage() {
        return bestDragonDamage;
    }

    public void bestDragonDamage(double bestDragonDamage) {
        this.bestDragonDamage = Math.max(0.0D, bestDragonDamage);
    }

    public void recordDragonDamage(double damage) {
        bestDragonDamage(Math.max(bestDragonDamage, damage));
    }

    public int dragonKills(String dragonId) {
        return dragonKills.getOrDefault(dragonId.toUpperCase(), 0);
    }

    public void setDragonKills(String dragonId, int amount) {
        String normalized = dragonId.toUpperCase();
        if (amount <= 0) {
            dragonKills.remove(normalized);
            return;
        }
        dragonKills.put(normalized, amount);
    }

    public void addDragonKill(String dragonId, int amount) {
        setDragonKills(dragonId, dragonKills(dragonId) + amount);
    }

    public long totalDragonKills() {
        return dragonKills.values().stream().mapToLong(Integer::longValue).sum();
    }

    public Map<String, Integer> dragonKills() {
        return dragonKills;
    }

    public long riftMotes() {
        return riftMotes;
    }

    public void riftMotes(long riftMotes) {
        this.riftMotes = Math.max(0L, riftMotes);
    }

    public void addRiftMotes(long amount) {
        riftMotes(riftMotes + amount);
    }

    public long riftSoulExchanges() {
        return riftSoulExchanges;
    }

    public void riftSoulExchanges(long riftSoulExchanges) {
        this.riftSoulExchanges = Math.max(0L, riftSoulExchanges);
    }

    public void addRiftSoulExchanges(long amount) {
        riftSoulExchanges(riftSoulExchanges + amount);
    }

    public long riftEntries() {
        return riftEntries;
    }

    public void riftEntries(long riftEntries) {
        this.riftEntries = Math.max(0L, riftEntries);
    }

    public void addRiftEntries(long amount) {
        riftEntries(riftEntries + amount);
    }

    public long riftTimeSpentSeconds() {
        return riftTimeSpentSeconds;
    }

    public void riftTimeSpentSeconds(long riftTimeSpentSeconds) {
        this.riftTimeSpentSeconds = Math.max(0L, riftTimeSpentSeconds);
    }

    public void addRiftTimeSpentSeconds(long amount) {
        riftTimeSpentSeconds(riftTimeSpentSeconds + amount);
    }

    public long riftOrbsCollected() {
        return riftOrbsCollected;
    }

    public void riftOrbsCollected(long riftOrbsCollected) {
        this.riftOrbsCollected = Math.max(0L, riftOrbsCollected);
    }

    public void addRiftOrbsCollected(long amount) {
        riftOrbsCollected(riftOrbsCollected + amount);
    }

    public boolean hasRiftTimecharm(String timecharmId) {
        return riftTimecharms.contains(timecharmId.toUpperCase());
    }

    public boolean addRiftTimecharm(String timecharmId) {
        return riftTimecharms.add(timecharmId.toUpperCase());
    }

    public Set<String> riftTimecharms() {
        return riftTimecharms;
    }

    public boolean hasRiftSoul(String soulId) {
        return riftSouls.contains(soulId.toUpperCase());
    }

    public boolean addRiftSoul(String soulId) {
        return riftSouls.add(soulId.toUpperCase());
    }

    public Set<String> riftSouls() {
        return riftSouls;
    }

    public int kuudraCompletions(String tierId) {
        return kuudraCompletions.getOrDefault(tierId.toUpperCase(), 0);
    }

    public void setKuudraCompletions(String tierId, int amount) {
        String normalized = tierId.toUpperCase();
        if (amount <= 0) {
            kuudraCompletions.remove(normalized);
            return;
        }
        kuudraCompletions.put(normalized, amount);
    }

    public void addKuudraCompletion(String tierId, int amount) {
        setKuudraCompletions(tierId, kuudraCompletions(tierId) + amount);
    }

    public long totalKuudraCompletions() {
        return kuudraCompletions.values().stream().mapToLong(Integer::longValue).sum();
    }

    public Map<String, Integer> kuudraCompletions() {
        return kuudraCompletions;
    }

    public long kuudraTeeth() {
        return kuudraTeeth;
    }

    public void kuudraTeeth(long kuudraTeeth) {
        this.kuudraTeeth = Math.max(0L, kuudraTeeth);
    }

    public void addKuudraTeeth(long amount) {
        kuudraTeeth(kuudraTeeth + amount);
    }

    public long kuudraKeysCrafted() {
        return kuudraKeysCrafted;
    }

    public void kuudraKeysCrafted(long kuudraKeysCrafted) {
        this.kuudraKeysCrafted = Math.max(0L, kuudraKeysCrafted);
    }

    public void addKuudraKeysCrafted(long amount) {
        kuudraKeysCrafted(kuudraKeysCrafted + amount);
    }

    public long kuudraKeysUsed() {
        return kuudraKeysUsed;
    }

    public void kuudraKeysUsed(long kuudraKeysUsed) {
        this.kuudraKeysUsed = Math.max(0L, kuudraKeysUsed);
    }

    public void addKuudraKeysUsed(long amount) {
        kuudraKeysUsed(kuudraKeysUsed + amount);
    }

    public int bestKuudraScore() {
        return bestKuudraScore;
    }

    public void bestKuudraScore(int bestKuudraScore) {
        this.bestKuudraScore = Math.max(0, bestKuudraScore);
    }

    public void recordKuudraScore(int score) {
        bestKuudraScore(Math.max(bestKuudraScore, score));
    }

    public String selectedFaction() {
        return selectedFaction;
    }

    public void selectedFaction(String selectedFaction) {
        this.selectedFaction = selectedFaction == null || selectedFaction.isBlank() ? null : selectedFaction.toUpperCase();
    }

    public long factionReputation(String factionId) {
        return factionReputation.getOrDefault(factionId.toUpperCase(), 0L);
    }

    public void setFactionReputation(String factionId, long amount) {
        String normalized = factionId.toUpperCase();
        if (amount <= 0L) {
            factionReputation.remove(normalized);
            return;
        }
        factionReputation.put(normalized, amount);
    }

    public void addFactionReputation(String factionId, long amount) {
        setFactionReputation(factionId, factionReputation(factionId) + amount);
    }

    public Map<String, Long> factionReputation() {
        return factionReputation;
    }

    public int factionQuestCompletions(String questId) {
        return factionQuestCompletions.getOrDefault(questId.toUpperCase(), 0);
    }

    public void setFactionQuestCompletions(String questId, int amount) {
        String normalized = questId.toUpperCase();
        if (amount <= 0) {
            factionQuestCompletions.remove(normalized);
            return;
        }
        factionQuestCompletions.put(normalized, amount);
    }

    public void addFactionQuestCompletion(String questId, int amount) {
        setFactionQuestCompletions(questId, factionQuestCompletions(questId) + amount);
    }

    public Map<String, Integer> factionQuestCompletions() {
        return factionQuestCompletions;
    }

    public int factionMinibossKills(String minibossId) {
        return factionMinibossKills.getOrDefault(minibossId.toUpperCase(), 0);
    }

    public void setFactionMinibossKills(String minibossId, int amount) {
        String normalized = minibossId.toUpperCase();
        if (amount <= 0) {
            factionMinibossKills.remove(normalized);
            return;
        }
        factionMinibossKills.put(normalized, amount);
    }

    public void addFactionMinibossKill(String minibossId, int amount) {
        setFactionMinibossKills(minibossId, factionMinibossKills(minibossId) + amount);
    }

    public Map<String, Integer> factionMinibossKills() {
        return factionMinibossKills;
    }

    public String factionDay() {
        return factionDay;
    }

    public void factionDay(String factionDay) {
        this.factionDay = factionDay;
    }

    public int dailyFactionQuests() {
        return dailyFactionQuests;
    }

    public void dailyFactionQuests(int dailyFactionQuests) {
        this.dailyFactionQuests = Math.max(0, dailyFactionQuests);
    }

    public void addDailyFactionQuest() {
        dailyFactionQuests(dailyFactionQuests + 1);
    }

    public int dailyFactionMinibosses() {
        return dailyFactionMinibosses;
    }

    public void dailyFactionMinibosses(int dailyFactionMinibosses) {
        this.dailyFactionMinibosses = Math.max(0, dailyFactionMinibosses);
    }

    public void addDailyFactionMiniboss() {
        dailyFactionMinibosses(dailyFactionMinibosses + 1);
    }

    public int dojoChallengeScore(String challengeId) {
        return dojoChallengeScores.getOrDefault(challengeId.toUpperCase(), 0);
    }

    public void setDojoChallengeScore(String challengeId, int score) {
        String normalized = challengeId.toUpperCase();
        if (score <= 0) {
            dojoChallengeScores.remove(normalized);
            return;
        }
        dojoChallengeScores.put(normalized, score);
    }

    public Map<String, Integer> dojoChallengeScores() {
        return dojoChallengeScores;
    }

    public boolean hasClaimedDojoBelt(String beltId) {
        return claimedDojoBelts.contains(beltId.toUpperCase());
    }

    public boolean claimDojoBelt(String beltId) {
        return claimedDojoBelts.add(beltId.toUpperCase());
    }

    public Set<String> claimedDojoBelts() {
        return claimedDojoBelts;
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

    public Map<Integer, ActiveForgeJob> forgeJobs() {
        return forgeJobs;
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
