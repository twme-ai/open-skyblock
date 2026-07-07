package io.github.openskyblock.profile;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.pet.AutoPetRule;
import io.github.openskyblock.pet.AutoPetTrigger;
import io.github.openskyblock.service.SkillType;
import io.github.openskyblock.wardrobe.WardrobeSet;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProfileManager {
    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final Map<UUID, SkyBlockProfile> profiles = new HashMap<>();
    private File profileFile;
    private YamlConfiguration profileData;

    public ProfileManager(JavaPlugin plugin, ConfigService configService) {
        this.plugin = plugin;
        this.configService = configService;
    }

    public void loadAll() {
        this.profileFile = new File(plugin.getDataFolder(), "profiles.yml");
        if (!profileFile.exists()) {
            try {
                File parent = profileFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }
                profileFile.createNewFile();
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to create profiles.yml", exception);
            }
        }
        this.profileData = YamlConfiguration.loadConfiguration(profileFile);
        ConfigurationSection section = profileData.getConfigurationSection("profiles");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID uniqueId = UUID.fromString(key);
                profiles.put(uniqueId, loadProfile(uniqueId, section.getConfigurationSection(key)));
            } catch (IllegalArgumentException ignored) {
                plugin.getLogger().warning("Skipping invalid profile UUID in profiles.yml: " + key);
            }
        }
    }

    public SkyBlockProfile profile(Player player) {
        SkyBlockProfile profile = profiles.computeIfAbsent(player.getUniqueId(), uniqueId -> newProfile(uniqueId, player.getName()));
        profile.playerName(player.getName());
        return profile;
    }

    public SkyBlockProfile profile(UUID uniqueId) {
        return profiles.get(uniqueId);
    }

    public Collection<SkyBlockProfile> loadedProfiles() {
        return profiles.values();
    }

    public SkyBlockProfile profileByIslandWorld(String worldName) {
        if (worldName == null || worldName.isBlank()) {
            return null;
        }
        for (SkyBlockProfile profile : profiles.values()) {
            if (worldName.equals(profile.islandWorldName())) {
                return profile;
            }
        }
        return null;
    }

    public void save(Player player) {
        SkyBlockProfile profile = profiles.get(player.getUniqueId());
        if (profile != null) {
            writeProfile(profile);
            saveFile();
        }
    }

    public void saveAll() {
        for (SkyBlockProfile profile : profiles.values()) {
            writeProfile(profile);
        }
        saveFile();
    }

    private SkyBlockProfile newProfile(UUID uniqueId, String playerName) {
        double purse = configService.main().getDouble("settings.default-purse", 0.0D);
        double bank = configService.main().getDouble("settings.default-bank", 0.0D);
        SkyBlockProfile profile = new SkyBlockProfile(uniqueId, playerName, purse, bank);
        profile.islandVisitorsEnabled(configService.main().getBoolean("islands.default-visitors-enabled", false));
        return profile;
    }

    public SkyBlockProfile profileByName(String playerName) {
        if (playerName == null || playerName.isBlank()) {
            return null;
        }
        for (SkyBlockProfile profile : profiles.values()) {
            if (profile.playerName().equalsIgnoreCase(playerName)) {
                return profile;
            }
        }
        return null;
    }

    public String name(UUID uniqueId) {
        SkyBlockProfile profile = profiles.get(uniqueId);
        if (profile != null) {
            return profile.playerName();
        }
        String name = Bukkit.getOfflinePlayer(uniqueId).getName();
        return name == null || name.isBlank() ? uniqueId.toString() : name;
    }

    public void save(SkyBlockProfile profile) {
        if (profile != null) {
            writeProfile(profile);
            saveFile();
        }
    }

    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private SkyBlockProfile loadProfile(UUID uniqueId, ConfigurationSection section) {
        if (section == null) {
            return newProfile(uniqueId, "Unknown");
        }
        SkyBlockProfile profile = new SkyBlockProfile(
                uniqueId,
                section.getString("name", "Unknown"),
                section.getDouble("purse", 0.0D),
                section.getDouble("bank", 0.0D)
        );
        profile.bankInterestLastMillis(section.getLong("bank-interest.last-millis", System.currentTimeMillis()));
        profile.islandWorldName(section.getString("island-world", null));
        profile.islandVisitorsEnabled(section.getBoolean("island.visitors-enabled", configService.main().getBoolean("islands.default-visitors-enabled", false)));
        ConfigurationSection islandHome = section.getConfigurationSection("island.home");
        if (islandHome != null && islandHome.getBoolean("set", false)) {
            profile.islandHome(
                    islandHome.getDouble("x", 0.5D),
                    islandHome.getDouble("y", configService.main().getDouble("islands.spawn-y", 80.0D) + 2.0D),
                    islandHome.getDouble("z", 0.5D),
                    (float) islandHome.getDouble("yaw", configService.main().getDouble("islands.home-yaw", 180.0D)),
                    (float) islandHome.getDouble("pitch", configService.main().getDouble("islands.home-pitch", 0.0D))
            );
        }
        ConfigurationSection islandWarps = section.getConfigurationSection("island.warps");
        if (islandWarps != null) {
            for (String warpId : islandWarps.getKeys(false)) {
                ConfigurationSection warp = islandWarps.getConfigurationSection(warpId);
                if (warp == null || warpId.isBlank()) {
                    continue;
                }
                profile.setIslandWarp(new IslandWarp(
                        warpId.toLowerCase(Locale.ROOT),
                        warp.getDouble("x", 0.5D),
                        warp.getDouble("y", configService.main().getDouble("islands.spawn-y", 80.0D) + 2.0D),
                        warp.getDouble("z", 0.5D),
                        (float) warp.getDouble("yaw", configService.main().getDouble("islands.home-yaw", 180.0D)),
                        (float) warp.getDouble("pitch", configService.main().getDouble("islands.home-pitch", 0.0D))
                ));
            }
        }
        ConfigurationSection islandPads = section.getConfigurationSection("island.teleport-pads");
        if (islandPads != null) {
            for (String padId : islandPads.getKeys(false)) {
                ConfigurationSection pad = islandPads.getConfigurationSection(padId);
                if (pad == null || padId.isBlank()) {
                    continue;
                }
                profile.setIslandTeleportPad(new IslandTeleportPad(
                        padId.toLowerCase(Locale.ROOT),
                        pad.getString("group", "default").toLowerCase(Locale.ROOT),
                        pad.getInt("x", 0),
                        pad.getInt("y", configService.main().getInt("islands.spawn-y", 80) + 1),
                        pad.getInt("z", 0),
                        (float) pad.getDouble("yaw", configService.main().getDouble("islands.home-yaw", 180.0D)),
                        (float) pad.getDouble("pitch", configService.main().getDouble("islands.home-pitch", 0.0D))
                ));
            }
        }
        for (String rawMember : section.getStringList("island.coop-members")) {
            UUID memberId = parseUuid(rawMember);
            if (memberId != null) {
                profile.addIslandCoopMember(memberId);
            }
        }
        ConfigurationSection coopRoles = section.getConfigurationSection("island.coop-roles");
        if (coopRoles != null) {
            for (String rawMemberId : coopRoles.getKeys(false)) {
                UUID memberId = parseUuid(rawMemberId);
                String roleId = coopRoles.getString(rawMemberId);
                if (memberId != null && roleId != null && !roleId.isBlank()) {
                    profile.setIslandCoopRole(memberId, roleId);
                }
            }
        }
        ConfigurationSection skills = section.getConfigurationSection("skills");
        if (skills != null) {
            for (String key : skills.getKeys(false)) {
                SkillType.fromKey(key).ifPresent(type -> profile.setSkillXp(type, skills.getDouble(key + ".xp", 0.0D)));
            }
        }
        ConfigurationSection collections = section.getConfigurationSection("collections");
        if (collections != null) {
            for (String key : collections.getKeys(false)) {
                profile.setCollectionAmount(key.toUpperCase(), collections.getLong(key, 0L));
            }
        }
        for (String soulId : section.getStringList("fairy-souls.found")) {
            if (soulId != null && !soulId.isBlank()) {
                profile.addFairySoul(soulId);
            }
        }
        profile.fairySoulExchanges(section.getLong("fairy-souls.exchanges", 0L));
        ConfigurationSection trophyFish = section.getConfigurationSection("trophy-fish.catches");
        if (trophyFish != null) {
            for (String fishId : trophyFish.getKeys(false)) {
                ConfigurationSection tiers = trophyFish.getConfigurationSection(fishId);
                if (tiers == null) {
                    continue;
                }
                for (String tier : tiers.getKeys(false)) {
                    profile.setTrophyFish(fishId, tier, tiers.getLong(tier, 0L));
                }
            }
        }
        profile.experimentDay(section.getString("experiments.day", null));
        profile.experimentBonusClicks(section.getInt("experiments.bonus-clicks", 0));
        ConfigurationSection experiments = section.getConfigurationSection("experiments.completions");
        if (experiments != null) {
            for (String experimentId : experiments.getKeys(false)) {
                profile.setExperimentCompletions(experimentId.toUpperCase(), experiments.getInt(experimentId, 0));
            }
        }
        profile.selectedDungeonClass(section.getString("dungeons.selected-class", null));
        profile.dungeonRunDay(section.getString("dungeons.daily.day", null));
        profile.dailyDungeonRuns(section.getInt("dungeons.daily.runs", 0));
        ConfigurationSection dungeonCompletions = section.getConfigurationSection("dungeons.completions");
        if (dungeonCompletions != null) {
            for (String floorId : dungeonCompletions.getKeys(false)) {
                profile.setDungeonCompletions(floorId.toUpperCase(), dungeonCompletions.getInt(floorId, 0));
            }
        }
        ConfigurationSection dungeonClassXp = section.getConfigurationSection("dungeons.class-xp");
        if (dungeonClassXp != null) {
            for (String classId : dungeonClassXp.getKeys(false)) {
                profile.setDungeonClassXp(classId.toUpperCase(), dungeonClassXp.getDouble(classId, 0.0D));
            }
        }
        profile.gardenXp(section.getDouble("garden.xp", 0.0D));
        profile.gardenCopper(section.getLong("garden.copper", 0L));
        profile.gardenCompost(section.getLong("garden.compost", 0L));
        profile.gardenVisitorOffers(section.getLong("garden.visitor-offers", 0L));
        for (String plotId : section.getStringList("garden.plots")) {
            if (plotId != null && !plotId.isBlank()) {
                profile.addGardenPlot(plotId);
            }
        }
        ConfigurationSection gardenHarvests = section.getConfigurationSection("garden.crops.harvests");
        if (gardenHarvests != null) {
            for (String cropId : gardenHarvests.getKeys(false)) {
                profile.setGardenCropHarvests(cropId.toUpperCase(), gardenHarvests.getLong(cropId, 0L));
            }
        }
        ConfigurationSection gardenStorage = section.getConfigurationSection("garden.crops.storage");
        if (gardenStorage != null) {
            for (String cropId : gardenStorage.getKeys(false)) {
                profile.setGardenCropStorage(cropId.toUpperCase(), gardenStorage.getLong(cropId, 0L));
            }
        }
        ConfigurationSection gardenVisitors = section.getConfigurationSection("garden.visitors");
        if (gardenVisitors != null) {
            for (String visitorId : gardenVisitors.getKeys(false)) {
                profile.setGardenVisitorServed(visitorId.toUpperCase(), gardenVisitors.getInt(visitorId, 0));
            }
        }
        profile.placedDragonEyes(section.getInt("dragons.placed-eyes", 0));
        profile.summoningEyesUsed(section.getLong("dragons.summoning-eyes-used", 0L));
        profile.bestDragonDamage(section.getDouble("dragons.best-damage", 0.0D));
        ConfigurationSection dragonKills = section.getConfigurationSection("dragons.kills");
        if (dragonKills != null) {
            for (String dragonId : dragonKills.getKeys(false)) {
                profile.setDragonKills(dragonId.toUpperCase(), dragonKills.getInt(dragonId, 0));
            }
        }
        profile.riftMotes(section.getLong("rift.motes", 0L));
        profile.riftSoulExchanges(section.getLong("rift.soul-exchanges", 0L));
        profile.riftEntries(section.getLong("rift.entries", 0L));
        profile.riftTimeSpentSeconds(section.getLong("rift.time-spent-seconds", 0L));
        profile.riftOrbsCollected(section.getLong("rift.orbs-collected", 0L));
        for (String timecharmId : section.getStringList("rift.timecharms")) {
            if (timecharmId != null && !timecharmId.isBlank()) {
                profile.addRiftTimecharm(timecharmId);
            }
        }
        for (String soulId : section.getStringList("rift.souls")) {
            if (soulId != null && !soulId.isBlank()) {
                profile.addRiftSoul(soulId);
            }
        }
        profile.kuudraTeeth(section.getLong("kuudra.teeth", 0L));
        profile.kuudraKeysCrafted(section.getLong("kuudra.keys-crafted", 0L));
        profile.kuudraKeysUsed(section.getLong("kuudra.keys-used", 0L));
        profile.bestKuudraScore(section.getInt("kuudra.best-score", 0));
        ConfigurationSection kuudraCompletions = section.getConfigurationSection("kuudra.completions");
        if (kuudraCompletions != null) {
            for (String tierId : kuudraCompletions.getKeys(false)) {
                profile.setKuudraCompletions(tierId.toUpperCase(), kuudraCompletions.getInt(tierId, 0));
            }
        }
        profile.selectedFaction(section.getString("factions.selected", null));
        profile.factionDay(section.getString("factions.daily.day", null));
        profile.dailyFactionQuests(section.getInt("factions.daily.quests", 0));
        profile.dailyFactionMinibosses(section.getInt("factions.daily.minibosses", 0));
        ConfigurationSection factionReputation = section.getConfigurationSection("factions.reputation");
        if (factionReputation != null) {
            for (String factionId : factionReputation.getKeys(false)) {
                profile.setFactionReputation(factionId.toUpperCase(), factionReputation.getLong(factionId, 0L));
            }
        }
        ConfigurationSection factionQuests = section.getConfigurationSection("factions.quests");
        if (factionQuests != null) {
            for (String questId : factionQuests.getKeys(false)) {
                profile.setFactionQuestCompletions(questId.toUpperCase(), factionQuests.getInt(questId, 0));
            }
        }
        ConfigurationSection factionMinibosses = section.getConfigurationSection("factions.minibosses");
        if (factionMinibosses != null) {
            for (String minibossId : factionMinibosses.getKeys(false)) {
                profile.setFactionMinibossKills(minibossId.toUpperCase(), factionMinibosses.getInt(minibossId, 0));
            }
        }
        ConfigurationSection dojoScores = section.getConfigurationSection("dojo.scores");
        if (dojoScores != null) {
            for (String challengeId : dojoScores.getKeys(false)) {
                profile.setDojoChallengeScore(challengeId.toUpperCase(), dojoScores.getInt(challengeId, 0));
            }
        }
        for (String beltId : section.getStringList("dojo.claimed-belts")) {
            if (beltId != null && !beltId.isBlank()) {
                profile.claimDojoBelt(beltId);
            }
        }
        profile.mythologicalGriffinRarity(section.getString("mythological.griffin-rarity", null));
        profile.mythologicalBurrowChain(section.getInt("mythological.burrow-chain", 0));
        profile.mythologicalBurrowsDug(section.getLong("mythological.burrows-dug", 0L));
        ConfigurationSection mythologicalMobs = section.getConfigurationSection("mythological.mobs");
        if (mythologicalMobs != null) {
            for (String mobId : mythologicalMobs.getKeys(false)) {
                profile.setMythologicalMobKills(mobId.toUpperCase(), mythologicalMobs.getInt(mobId, 0));
            }
        }
        ConfigurationSection mythologicalTreasures = section.getConfigurationSection("mythological.treasures");
        if (mythologicalTreasures != null) {
            for (String treasureId : mythologicalTreasures.getKeys(false)) {
                profile.setMythologicalTreasures(treasureId.toUpperCase(), mythologicalTreasures.getInt(treasureId, 0));
            }
        }
        profile.spookyGreenCandy(section.getLong("spooky.green-candy", 0L));
        profile.spookyPurpleCandy(section.getLong("spooky.purple-candy", 0L));
        ConfigurationSection spookyMobs = section.getConfigurationSection("spooky.mobs");
        if (spookyMobs != null) {
            for (String mobId : spookyMobs.getKeys(false)) {
                profile.setSpookyMobKills(mobId.toUpperCase(), spookyMobs.getInt(mobId, 0));
            }
        }
        for (String rewardId : section.getStringList("spooky.claimed-rewards")) {
            if (rewardId != null && !rewardId.isBlank()) {
                profile.claimSpookyReward(rewardId);
            }
        }
        ConfigurationSection zooPurchases = section.getConfigurationSection("traveling-zoo.purchases");
        if (zooPurchases != null) {
            for (String petId : zooPurchases.getKeys(false)) {
                profile.setZooPurchases(petId.toUpperCase(), zooPurchases.getInt(petId, 0));
            }
        }
        profile.jerryGiftYear(section.getInt("season-jerry.year", 0));
        profile.jerryHiddenGiftsFound(section.getInt("season-jerry.hidden-gifts", 0));
        profile.jerryStJerryClaimedYear(section.getInt("season-jerry.st-jerry-claimed-year", 0));
        profile.jerryGiftAttackWaves(section.getInt("season-jerry.gift-attack-waves", 0));
        profile.jerryNorthStars(section.getLong("season-jerry.north-stars", 0L));
        ConfigurationSection jerryGifts = section.getConfigurationSection("season-jerry.gifts-opened");
        if (jerryGifts != null) {
            for (String giftId : jerryGifts.getKeys(false)) {
                profile.setJerryGiftsOpened(giftId.toUpperCase(), jerryGifts.getInt(giftId, 0));
            }
        }
        for (Integer year : section.getIntegerList("new-year.claimed-cakes")) {
            profile.claimNewYearCake(year);
        }
        profile.newYearCakeBagOwned(section.getBoolean("new-year.cake-bag-owned", false));
        for (Integer year : section.getIntegerList("new-year.cake-bag-years")) {
            profile.storeNewYearCake(year);
        }
        profile.chocolate(section.getDouble("chocolate-factory.chocolate", 0.0D));
        profile.allTimeChocolate(section.getDouble("chocolate-factory.all-time", 0.0D));
        profile.chocolateThisPrestige(section.getDouble("chocolate-factory.this-prestige", 0.0D));
        profile.chocolateSpent(section.getDouble("chocolate-factory.spent", 0.0D));
        profile.chocolateLastAccrualMillis(section.getLong("chocolate-factory.last-accrual-millis", 0L));
        profile.chocolateFactoryLevel(section.getInt("chocolate-factory.factory-level", 1));
        profile.hoppityEggsFound(section.getLong("chocolate-factory.hoppity-eggs", 0L));
        profile.hoppityEggClaimKey(section.getString("chocolate-factory.hoppity-egg-claim-key", null));
        profile.hoppityShopYear(section.getInt("chocolate-factory.hoppity-shop-year", 0));
        profile.chocolateShopYear(section.getInt("chocolate-factory.shop-year", 0));
        ConfigurationSection chocolateUpgrades = section.getConfigurationSection("chocolate-factory.upgrades");
        if (chocolateUpgrades != null) {
            for (String upgradeId : chocolateUpgrades.getKeys(false)) {
                profile.setChocolateUpgradeLevel(upgradeId.toUpperCase(), chocolateUpgrades.getInt(upgradeId, 0));
            }
        }
        ConfigurationSection chocolateRabbits = section.getConfigurationSection("chocolate-factory.rabbits");
        if (chocolateRabbits != null) {
            for (String rabbitId : chocolateRabbits.getKeys(false)) {
                profile.setChocolateRabbitCopies(rabbitId.toUpperCase(), chocolateRabbits.getInt(rabbitId, 0));
            }
        }
        for (String rabbitId : section.getStringList("chocolate-factory.hoppity-shop-purchases")) {
            if (rabbitId != null && !rabbitId.isBlank()) {
                profile.addHoppityShopPurchase(rabbitId);
            }
        }
        ConfigurationSection chocolateShopPurchases = section.getConfigurationSection("chocolate-factory.shop-purchases");
        if (chocolateShopPurchases != null) {
            for (String itemId : chocolateShopPurchases.getKeys(false)) {
                profile.setChocolateShopPurchases(itemId.toUpperCase(), chocolateShopPurchases.getInt(itemId, 0));
            }
        }
        profile.miningFiestaBlocks(section.getLong("mining-fiesta.blocks", 0L));
        profile.miningFiestaRefinedMinerals(section.getLong("mining-fiesta.refined-minerals", 0L));
        profile.miningFiestaGlossyGemstones(section.getLong("mining-fiesta.glossy-gemstones", 0L));
        ConfigurationSection miningFiestaBuffs = section.getConfigurationSection("mining-fiesta.buffs");
        if (miningFiestaBuffs != null) {
            for (String buffId : miningFiestaBuffs.getKeys(false)) {
                profile.setMiningFiestaBuff(buffId.toUpperCase(), miningFiestaBuffs.getLong(buffId, 0L));
            }
        }
        profile.fishingFestivalSharkCatches(section.getLong("fishing-festival.shark-catches", 0L));
        ConfigurationSection fishingFestivalSharks = section.getConfigurationSection("fishing-festival.sharks");
        if (fishingFestivalSharks != null) {
            for (String sharkId : fishingFestivalSharks.getKeys(false)) {
                profile.setFishingFestivalSharkCatches(sharkId.toUpperCase(), fishingFestivalSharks.getLong(sharkId, 0L));
            }
        }
        profile.shopPurchaseDay(section.getString("shop-purchases.day", null));
        ConfigurationSection shopPurchases = section.getConfigurationSection("shop-purchases.items");
        if (shopPurchases != null) {
            for (String key : shopPurchases.getKeys(false)) {
                profile.setDailyShopPurchases(key.toUpperCase(), shopPurchases.getInt(key, 0));
            }
        }
        for (String itemId : section.getStringList("accessory-bag")) {
            if (itemId != null && !itemId.isBlank()) {
                profile.addAccessory(itemId);
            }
        }
        ConfigurationSection tuning = section.getConfigurationSection("tuning");
        if (tuning != null) {
            for (String key : tuning.getKeys(false)) {
                profile.setTuning(key.toLowerCase(), tuning.getInt(key, 0));
            }
        }
        ConfigurationSection equipment = section.getConfigurationSection("equipment");
        if (equipment != null) {
            for (String key : equipment.getKeys(false)) {
                ItemStack itemStack = equipment.getItemStack(key);
                if (itemStack != null) {
                    profile.equipment().put(key.toUpperCase(), itemStack);
                }
            }
        }
        ConfigurationSection wardrobe = section.getConfigurationSection("wardrobe");
        if (wardrobe != null) {
            for (String key : wardrobe.getKeys(false)) {
                ConfigurationSection slotSection = wardrobe.getConfigurationSection(key);
                if (slotSection == null) {
                    continue;
                }
                try {
                    int slot = Integer.parseInt(key);
                    WardrobeSet set = WardrobeSet.of(
                            slotSection.getItemStack("helmet"),
                            slotSection.getItemStack("chestplate"),
                            slotSection.getItemStack("leggings"),
                            slotSection.getItemStack("boots")
                    );
                    if (!set.empty()) {
                        profile.wardrobe().put(slot, set);
                    }
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Skipping invalid wardrobe slot in profiles.yml: " + key);
                }
            }
        }
        ConfigurationSection storagePages = section.getConfigurationSection("storage.pages");
        if (storagePages != null) {
            for (String pageKey : storagePages.getKeys(false)) {
                ConfigurationSection pageSection = storagePages.getConfigurationSection(pageKey);
                if (pageSection == null) {
                    continue;
                }
                try {
                    int page = Integer.parseInt(pageKey);
                    ItemStack[] contents = new ItemStack[54];
                    for (String slotKey : pageSection.getKeys(false)) {
                        int slot = Integer.parseInt(slotKey);
                        if (slot < 0 || slot >= contents.length) {
                            continue;
                        }
                        ItemStack itemStack = pageSection.getItemStack(slotKey);
                        if (itemStack != null && !itemStack.getType().isAir()) {
                            contents[slot] = itemStack;
                        }
                    }
                    profile.storagePages().put(page, contents);
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Skipping invalid storage page or slot in profiles.yml: " + pageKey);
                }
            }
        }
        ConfigurationSection backpacks = section.getConfigurationSection("backpacks");
        if (backpacks != null) {
            for (String slotKey : backpacks.getKeys(false)) {
                ConfigurationSection backpackSection = backpacks.getConfigurationSection(slotKey);
                if (backpackSection == null) {
                    continue;
                }
                try {
                    int backpackSlot = Integer.parseInt(slotKey);
                    String id = backpackSection.getString("id", "");
                    if (id.isBlank()) {
                        continue;
                    }
                    ItemStack[] contents = new ItemStack[54];
                    ConfigurationSection items = backpackSection.getConfigurationSection("items");
                    if (items != null) {
                        for (String itemSlotKey : items.getKeys(false)) {
                            int itemSlot = Integer.parseInt(itemSlotKey);
                            if (itemSlot < 0 || itemSlot >= contents.length) {
                                continue;
                            }
                            ItemStack itemStack = items.getItemStack(itemSlotKey);
                            if (itemStack != null && !itemStack.getType().isAir()) {
                                contents[itemSlot] = itemStack;
                            }
                        }
                    }
                    profile.backpacks().put(backpackSlot, new OwnedBackpack(backpackSlot, id.toUpperCase(), contents));
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Skipping invalid backpack slot in profiles.yml: " + slotKey);
                }
            }
        }
        ConfigurationSection sacks = section.getConfigurationSection("sacks");
        if (sacks != null) {
            for (String sackId : sacks.getKeys(false)) {
                ConfigurationSection sackSection = sacks.getConfigurationSection(sackId);
                if (sackSection == null) {
                    continue;
                }
                Map<String, Long> storage = profile.sacks().computeIfAbsent(sackId.toUpperCase(), ignored -> new HashMap<>());
                for (String itemId : sackSection.getKeys(false)) {
                    long amount = sackSection.getLong(itemId, 0L);
                    if (amount > 0L) {
                        storage.put(itemId.toUpperCase(), amount);
                    }
                }
                if (storage.isEmpty()) {
                    profile.sacks().remove(sackId.toUpperCase());
                }
            }
        }
        profile.selectedQuiverItem(section.getString("quiver.selected", null));
        ConfigurationSection quiver = section.getConfigurationSection("quiver.items");
        if (quiver != null) {
            for (String itemId : quiver.getKeys(false)) {
                long amount = quiver.getLong(itemId, 0L);
                if (amount > 0L) {
                    profile.quiver().put(itemId.toUpperCase(), amount);
                }
            }
        }
        ConfigurationSection potions = section.getConfigurationSection("potions.active");
        if (potions != null) {
            for (String effectId : potions.getKeys(false)) {
                long seconds = potions.getLong(effectId, 0L);
                if (seconds > 0L) {
                    profile.potionEffects().put(effectId.toUpperCase(), seconds);
                }
            }
        }
        ConfigurationSection cakeBuffs = section.getConfigurationSection("cakes.active");
        if (cakeBuffs != null) {
            for (String cakeId : cakeBuffs.getKeys(false)) {
                long expiresAt = cakeBuffs.getLong(cakeId, 0L);
                if (expiresAt > System.currentTimeMillis()) {
                    profile.cakeBuffs().put(cakeId.toUpperCase(), expiresAt);
                }
            }
        }
        ConfigurationSection placedCakes = section.getConfigurationSection("cakes.placed");
        if (placedCakes != null) {
            for (String key : placedCakes.getKeys(false)) {
                ConfigurationSection cake = placedCakes.getConfigurationSection(key);
                if (cake != null) {
                    profile.placedCakes().add(new PlacedCake(
                            cake.getString("id", ""),
                            cake.getString("world", null),
                            cake.getInt("x", 0),
                            cake.getInt("y", 0),
                            cake.getInt("z", 0)
                    ));
                }
            }
        }
        ConfigurationSection upgrades = section.getConfigurationSection("upgrades");
        if (upgrades != null) {
            for (String upgradeId : upgrades.getKeys(false)) {
                int level = upgrades.getInt(upgradeId, 0);
                if (level > 0) {
                    profile.upgrades().put(upgradeId.toUpperCase(), level);
                }
            }
        }
        ConfigurationSection essence = section.getConfigurationSection("essence");
        if (essence != null) {
            for (String essenceId : essence.getKeys(false)) {
                double amount = essence.getDouble(essenceId, 0.0D);
                if (amount > 0.0D) {
                    profile.setEssence(essenceId.toUpperCase(), amount);
                }
            }
        }
        ConfigurationSection bestiaryKills = section.getConfigurationSection("bestiary.kills");
        if (bestiaryKills != null) {
            for (String familyId : bestiaryKills.getKeys(false)) {
                long kills = bestiaryKills.getLong(familyId, 0L);
                if (kills > 0L) {
                    profile.bestiaryKills().put(familyId.toUpperCase(), kills);
                }
            }
        }
        ConfigurationSection bestiaryTiers = section.getConfigurationSection("bestiary.tiers");
        if (bestiaryTiers != null) {
            for (String familyId : bestiaryTiers.getKeys(false)) {
                int tier = bestiaryTiers.getInt(familyId, 0);
                if (tier > 0) {
                    profile.bestiaryTiers().put(familyId.toUpperCase(), tier);
                }
            }
        }
        ConfigurationSection museumDonations = section.getConfigurationSection("museum.donations");
        if (museumDonations != null) {
            for (String donationId : museumDonations.getKeys(false)) {
                long donatedAtMillis = museumDonations.getLong(donationId, 0L);
                profile.addMuseumDonation(donationId, donatedAtMillis);
            }
        }
        ConfigurationSection darkAuctionPurchases = section.getConfigurationSection("dark-auction.purchases");
        if (darkAuctionPurchases != null) {
            for (String itemId : darkAuctionPurchases.getKeys(false)) {
                int amount = darkAuctionPurchases.getInt(itemId, 0);
                if (amount > 0) {
                    profile.setDarkAuctionPurchases(itemId.toUpperCase(), amount);
                }
            }
        }
        ConfigurationSection darkAuctionClaims = section.getConfigurationSection("dark-auction.claims");
        if (darkAuctionClaims != null) {
            for (String key : darkAuctionClaims.getKeys(false)) {
                ItemStack itemStack = darkAuctionClaims.getItemStack(key);
                if (itemStack != null && !itemStack.getType().isAir()) {
                    profile.darkAuctionClaims().add(itemStack);
                }
            }
        }
        ConfigurationSection mayorVotes = section.getConfigurationSection("mayors.votes");
        if (mayorVotes != null) {
            for (String electionId : mayorVotes.getKeys(false)) {
                String candidateId = mayorVotes.getString(electionId, "");
                if (!candidateId.isBlank()) {
                    profile.setMayorVote(electionId.toUpperCase(), candidateId.toUpperCase());
                }
            }
        }
        for (String electionId : section.getStringList("mayors.claimed-bribes")) {
            if (!electionId.isBlank()) {
                profile.setMayorBribeClaimed(electionId.toUpperCase(), true);
            }
        }
        profile.jacobsTickets(section.getLong("farming-contests.jacobs-tickets", 0L));
        ConfigurationSection farmingMedals = section.getConfigurationSection("farming-contests.medals");
        if (farmingMedals != null) {
            for (String medalId : farmingMedals.getKeys(false)) {
                long amount = farmingMedals.getLong(medalId, 0L);
                if (amount > 0L) {
                    profile.setFarmingContestMedal(medalId.toUpperCase(), amount);
                }
            }
        }
        ConfigurationSection farmingScores = section.getConfigurationSection("farming-contests.scores");
        if (farmingScores != null) {
            for (String contestId : farmingScores.getKeys(false)) {
                ConfigurationSection contest = farmingScores.getConfigurationSection(contestId);
                if (contest == null) {
                    continue;
                }
                for (String cropId : contest.getKeys(false)) {
                    long amount = contest.getLong(cropId, 0L);
                    if (amount > 0L) {
                        profile.addFarmingContestScore(contestId.toUpperCase(), cropId.toUpperCase(), amount);
                    }
                }
            }
        }
        ConfigurationSection farmingRewards = section.getConfigurationSection("farming-contests.rewards");
        if (farmingRewards != null) {
            for (String contestId : farmingRewards.getKeys(false)) {
                profile.setFarmingContestReward(contestId.toUpperCase(), farmingRewards.getString(contestId, "NONE"));
            }
        }
        profile.hotmXp(section.getDouble("commissions.hotm-xp", 0.0D));
        profile.totalCommissions(section.getLong("commissions.total", 0L));
        profile.commissionDay(section.getString("commissions.daily.day", null));
        profile.dailyCommissions(section.getInt("commissions.daily.completed", 0));
        ConfigurationSection powder = section.getConfigurationSection("commissions.powder");
        if (powder != null) {
            for (String powderId : powder.getKeys(false)) {
                double amount = powder.getDouble(powderId, 0.0D);
                if (amount > 0.0D) {
                    profile.setHotmPowder(powderId.toUpperCase(), amount);
                }
            }
        }
        ConfigurationSection activeCommissions = section.getConfigurationSection("commissions.active");
        if (activeCommissions != null) {
            for (String slotKey : activeCommissions.getKeys(false)) {
                ConfigurationSection active = activeCommissions.getConfigurationSection(slotKey);
                if (active == null) {
                    continue;
                }
                try {
                    int slot = Integer.parseInt(slotKey);
                    String id = active.getString("id", "");
                    if (!id.isBlank()) {
                        profile.activeCommissions().put(slot, new ActiveCommission(slot, id.toUpperCase(), active.getLong("progress", 0L)));
                    }
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Skipping invalid commission slot in profiles.yml: " + slotKey);
                }
            }
        }
        ConfigurationSection forgeJobs = section.getConfigurationSection("forge.jobs");
        if (forgeJobs != null) {
            for (String slotKey : forgeJobs.getKeys(false)) {
                ConfigurationSection job = forgeJobs.getConfigurationSection(slotKey);
                if (job == null) {
                    continue;
                }
                try {
                    int slot = Integer.parseInt(slotKey);
                    String id = job.getString("recipe", "");
                    if (!id.isBlank()) {
                        profile.forgeJobs().put(slot, new ActiveForgeJob(
                                slot,
                                id.toUpperCase(),
                                job.getLong("started-at-millis", 0L),
                                job.getLong("duration-millis", 0L)
                        ));
                    }
                } catch (NumberFormatException ignored) {
                    plugin.getLogger().warning("Skipping invalid forge slot in profiles.yml: " + slotKey);
                }
            }
        }
        ConfigurationSection cookies = section.getConfigurationSection("cookies");
        if (cookies != null) {
            profile.cookieBuffExpiresAtMillis(cookies.getLong("buff-expires-at", 0L));
            profile.bits(cookies.getLong("bits", 0L));
            profile.bitsAvailable(cookies.getLong("bits-available", 0L));
            profile.fameXp(cookies.getDouble("fame-xp", 0.0D));
            profile.cookiesConsumed(cookies.getLong("consumed", 0L));
            profile.bitsLastAccrualMillis(cookies.getLong("last-accrual-millis", 0L));
        }
        ConfigurationSection slayerXp = section.getConfigurationSection("slayers.xp");
        if (slayerXp != null) {
            for (String slayerId : slayerXp.getKeys(false)) {
                double xp = slayerXp.getDouble(slayerId, 0.0D);
                if (xp > 0.0D) {
                    profile.slayerXp().put(slayerId.toUpperCase(), xp);
                }
            }
        }
        ConfigurationSection slayerLevels = section.getConfigurationSection("slayers.levels");
        if (slayerLevels != null) {
            for (String slayerId : slayerLevels.getKeys(false)) {
                int level = slayerLevels.getInt(slayerId, 0);
                if (level > 0) {
                    profile.slayerLevels().put(slayerId.toUpperCase(), level);
                }
            }
        }
        ConfigurationSection activeSlayer = section.getConfigurationSection("slayers.active");
        if (activeSlayer != null) {
            String id = activeSlayer.getString("id", "");
            int tier = activeSlayer.getInt("tier", 0);
            if (!id.isBlank() && tier > 0) {
                profile.activeSlayer(new ActiveSlayerQuest(
                        id.toUpperCase(),
                        tier,
                        activeSlayer.getDouble("progress-xp", 0.0D),
                        activeSlayer.getBoolean("boss-spawned", false),
                        parseUuid(activeSlayer.getString("boss-entity-id", "")),
                        activeSlayer.getLong("boss-expires-at", 0L)
                ));
            }
        }
        profile.activePetInstanceId(section.getString("pets.active", null));
        ConfigurationSection pets = section.getConfigurationSection("pets.owned");
        if (pets != null) {
            for (String key : pets.getKeys(false)) {
                ConfigurationSection pet = pets.getConfigurationSection(key);
                if (pet != null) {
                    profile.pets().add(new OwnedPet(
                            pet.getString("instance-id", null),
                            pet.getString("id", ""),
                            pet.getDouble("xp", 0.0D),
                            pet.getString("pet-item", "")
                    ));
                }
            }
        }
        ConfigurationSection autoPetRules = section.getConfigurationSection("pets.autopet");
        if (autoPetRules != null) {
            for (String key : autoPetRules.getKeys(false)) {
                ConfigurationSection rule = autoPetRules.getConfigurationSection(key);
                if (rule == null) {
                    continue;
                }
                AutoPetTrigger.parse(rule.getString("trigger", ""))
                        .filter(trigger -> !rule.getString("pet", "").isBlank())
                        .map(trigger -> new AutoPetRule(trigger, rule.getString("pet", "")))
                        .ifPresent(profile.autoPetRules()::add);
            }
        }
        ConfigurationSection minions = section.getConfigurationSection("minions");
        if (minions != null) {
            for (String key : minions.getKeys(false)) {
                ConfigurationSection minion = minions.getConfigurationSection(key);
                if (minion != null) {
                    PlacedMinion placedMinion = new PlacedMinion(
                            minion.getString("id", ""),
                            minion.getLong("generated", 0L),
                            minion.getLong("last-action-millis", System.currentTimeMillis()),
                            minion.getString("world", null),
                            minion.getInt("x", 0),
                            minion.getInt("y", 0),
                            minion.getInt("z", 0),
                            minion.getString("fuel.id", ""),
                            minion.getLong("fuel.expires-at-millis", 0L)
                    );
                    for (String upgradeId : minion.getStringList("upgrades")) {
                        if (upgradeId != null && !upgradeId.isBlank()) {
                            placedMinion.upgradeIds().add(upgradeId.toUpperCase(Locale.ROOT));
                        }
                    }
                    if (!minion.getString("storage.id", "").isBlank()) {
                        placedMinion.storage(
                                minion.getString("storage.id", "").toUpperCase(Locale.ROOT),
                                minion.getString("storage.world", ""),
                                minion.getInt("storage.x", 0),
                                minion.getInt("storage.y", 0),
                                minion.getInt("storage.z", 0)
                        );
                    }
                    placedMinion.skinId(minion.getString("skin", "").toUpperCase(Locale.ROOT));
                    placedMinion.soldCoins(minion.getDouble("sold-coins", 0.0D));
                    profile.minions().add(placedMinion);
                }
            }
        }
        return profile;
    }

    private void writeProfile(SkyBlockProfile profile) {
        String base = "profiles." + profile.uniqueId();
        profileData.set(base + ".name", profile.playerName());
        profileData.set(base + ".purse", profile.purse());
        profileData.set(base + ".bank", profile.bank());
        profileData.set(base + ".bank-interest.last-millis", profile.bankInterestLastMillis());
        profileData.set(base + ".island-world", profile.islandWorldName());
        profileData.set(base + ".island.visitors-enabled", profile.islandVisitorsEnabled());
        profileData.set(base + ".island.coop-members", profile.islandCoopMembers().stream().map(UUID::toString).sorted().toList());
        profileData.set(base + ".island.coop-roles", null);
        for (UUID memberId : profile.islandCoopMembers().stream().sorted().toList()) {
            String roleId = profile.islandCoopRole(memberId);
            if (roleId != null && !roleId.isBlank()) {
                profileData.set(base + ".island.coop-roles." + memberId, roleId);
            }
        }
        profileData.set(base + ".island.home", null);
        profileData.set(base + ".island.home.set", profile.islandHomeSet());
        if (profile.islandHomeSet()) {
            profileData.set(base + ".island.home.x", profile.islandHomeX());
            profileData.set(base + ".island.home.y", profile.islandHomeY());
            profileData.set(base + ".island.home.z", profile.islandHomeZ());
            profileData.set(base + ".island.home.yaw", profile.islandHomeYaw());
            profileData.set(base + ".island.home.pitch", profile.islandHomePitch());
        }
        profileData.set(base + ".island.warps", null);
        for (IslandWarp warp : profile.islandWarps().values().stream()
                .sorted((first, second) -> first.id().compareTo(second.id()))
                .toList()) {
            String warpBase = base + ".island.warps." + warp.id();
            profileData.set(warpBase + ".x", warp.x());
            profileData.set(warpBase + ".y", warp.y());
            profileData.set(warpBase + ".z", warp.z());
            profileData.set(warpBase + ".yaw", warp.yaw());
            profileData.set(warpBase + ".pitch", warp.pitch());
        }
        profileData.set(base + ".island.teleport-pads", null);
        for (IslandTeleportPad pad : profile.islandTeleportPads().values().stream()
                .sorted((first, second) -> first.id().compareTo(second.id()))
                .toList()) {
            String padBase = base + ".island.teleport-pads." + pad.id();
            profileData.set(padBase + ".group", pad.group());
            profileData.set(padBase + ".x", pad.x());
            profileData.set(padBase + ".y", pad.y());
            profileData.set(padBase + ".z", pad.z());
            profileData.set(padBase + ".yaw", pad.yaw());
            profileData.set(padBase + ".pitch", pad.pitch());
        }
        for (Map.Entry<SkillType, Double> entry : profile.skillXp().entrySet()) {
            profileData.set(base + ".skills." + entry.getKey().key() + ".xp", entry.getValue());
        }
        for (Map.Entry<String, Long> entry : profile.collections().entrySet()) {
            profileData.set(base + ".collections." + entry.getKey(), entry.getValue());
        }
        profileData.set(base + ".fairy-souls", null);
        profileData.set(base + ".fairy-souls.found", profile.fairySouls().stream().sorted().toList());
        profileData.set(base + ".fairy-souls.exchanges", profile.fairySoulExchanges());
        profileData.set(base + ".trophy-fish", null);
        for (Map.Entry<String, Map<String, Long>> fishEntry : profile.trophyFish().entrySet()) {
            for (Map.Entry<String, Long> tierEntry : fishEntry.getValue().entrySet()) {
                if (tierEntry.getValue() > 0L) {
                    profileData.set(base + ".trophy-fish.catches." + fishEntry.getKey() + "." + tierEntry.getKey(), tierEntry.getValue());
                }
            }
        }
        profileData.set(base + ".experiments", null);
        profileData.set(base + ".experiments.day", profile.experimentDay());
        profileData.set(base + ".experiments.bonus-clicks", profile.experimentBonusClicks());
        for (Map.Entry<String, Integer> entry : profile.experimentCompletions().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".experiments.completions." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".dungeons", null);
        profileData.set(base + ".dungeons.selected-class", profile.selectedDungeonClass());
        profileData.set(base + ".dungeons.daily.day", profile.dungeonRunDay());
        profileData.set(base + ".dungeons.daily.runs", profile.dailyDungeonRuns());
        for (Map.Entry<String, Integer> entry : profile.dungeonCompletions().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".dungeons.completions." + entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Double> entry : profile.dungeonClassXp().entrySet()) {
            if (entry.getValue() > 0.0D) {
                profileData.set(base + ".dungeons.class-xp." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".garden", null);
        profileData.set(base + ".garden.xp", profile.gardenXp());
        profileData.set(base + ".garden.copper", profile.gardenCopper());
        profileData.set(base + ".garden.compost", profile.gardenCompost());
        profileData.set(base + ".garden.visitor-offers", profile.gardenVisitorOffers());
        profileData.set(base + ".garden.plots", profile.gardenPlots().stream().sorted().toList());
        for (Map.Entry<String, Long> entry : profile.gardenCropHarvests().entrySet()) {
            if (entry.getValue() > 0L) {
                profileData.set(base + ".garden.crops.harvests." + entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Long> entry : profile.gardenCropStorage().entrySet()) {
            if (entry.getValue() > 0L) {
                profileData.set(base + ".garden.crops.storage." + entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Integer> entry : profile.gardenVisitorsServed().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".garden.visitors." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".dragons", null);
        profileData.set(base + ".dragons.placed-eyes", profile.placedDragonEyes());
        profileData.set(base + ".dragons.summoning-eyes-used", profile.summoningEyesUsed());
        profileData.set(base + ".dragons.best-damage", profile.bestDragonDamage());
        for (Map.Entry<String, Integer> entry : profile.dragonKills().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".dragons.kills." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".rift", null);
        profileData.set(base + ".rift.motes", profile.riftMotes());
        profileData.set(base + ".rift.soul-exchanges", profile.riftSoulExchanges());
        profileData.set(base + ".rift.entries", profile.riftEntries());
        profileData.set(base + ".rift.time-spent-seconds", profile.riftTimeSpentSeconds());
        profileData.set(base + ".rift.orbs-collected", profile.riftOrbsCollected());
        profileData.set(base + ".rift.timecharms", profile.riftTimecharms().stream().sorted().toList());
        profileData.set(base + ".rift.souls", profile.riftSouls().stream().sorted().toList());
        profileData.set(base + ".kuudra", null);
        profileData.set(base + ".kuudra.teeth", profile.kuudraTeeth());
        profileData.set(base + ".kuudra.keys-crafted", profile.kuudraKeysCrafted());
        profileData.set(base + ".kuudra.keys-used", profile.kuudraKeysUsed());
        profileData.set(base + ".kuudra.best-score", profile.bestKuudraScore());
        for (Map.Entry<String, Integer> entry : profile.kuudraCompletions().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".kuudra.completions." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".factions", null);
        profileData.set(base + ".factions.selected", profile.selectedFaction());
        profileData.set(base + ".factions.daily.day", profile.factionDay());
        profileData.set(base + ".factions.daily.quests", profile.dailyFactionQuests());
        profileData.set(base + ".factions.daily.minibosses", profile.dailyFactionMinibosses());
        for (Map.Entry<String, Long> entry : profile.factionReputation().entrySet()) {
            if (entry.getValue() > 0L) {
                profileData.set(base + ".factions.reputation." + entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Integer> entry : profile.factionQuestCompletions().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".factions.quests." + entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Integer> entry : profile.factionMinibossKills().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".factions.minibosses." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".dojo", null);
        for (Map.Entry<String, Integer> entry : profile.dojoChallengeScores().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".dojo.scores." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".dojo.claimed-belts", profile.claimedDojoBelts().stream().sorted().toList());
        profileData.set(base + ".mythological", null);
        profileData.set(base + ".mythological.griffin-rarity", profile.mythologicalGriffinRarity());
        profileData.set(base + ".mythological.burrow-chain", profile.mythologicalBurrowChain());
        profileData.set(base + ".mythological.burrows-dug", profile.mythologicalBurrowsDug());
        for (Map.Entry<String, Integer> entry : profile.mythologicalMobKills().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".mythological.mobs." + entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Integer> entry : profile.mythologicalTreasures().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".mythological.treasures." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".spooky", null);
        profileData.set(base + ".spooky.green-candy", profile.spookyGreenCandy());
        profileData.set(base + ".spooky.purple-candy", profile.spookyPurpleCandy());
        for (Map.Entry<String, Integer> entry : profile.spookyMobKills().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".spooky.mobs." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".spooky.claimed-rewards", profile.claimedSpookyRewards().stream().sorted().toList());
        profileData.set(base + ".traveling-zoo", null);
        for (Map.Entry<String, Integer> entry : profile.zooPurchases().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".traveling-zoo.purchases." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".season-jerry", null);
        profileData.set(base + ".season-jerry.year", profile.jerryGiftYear());
        profileData.set(base + ".season-jerry.hidden-gifts", profile.jerryHiddenGiftsFound());
        profileData.set(base + ".season-jerry.st-jerry-claimed-year", profile.jerryStJerryClaimedYear());
        profileData.set(base + ".season-jerry.gift-attack-waves", profile.jerryGiftAttackWaves());
        profileData.set(base + ".season-jerry.north-stars", profile.jerryNorthStars());
        for (Map.Entry<String, Integer> entry : profile.jerryGiftsOpened().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".season-jerry.gifts-opened." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".new-year", null);
        profileData.set(base + ".new-year.claimed-cakes", profile.claimedNewYearCakeYears().stream().sorted().toList());
        profileData.set(base + ".new-year.cake-bag-owned", profile.newYearCakeBagOwned());
        profileData.set(base + ".new-year.cake-bag-years", profile.newYearCakeBagYears().stream().sorted().toList());
        profileData.set(base + ".chocolate-factory", null);
        profileData.set(base + ".chocolate-factory.chocolate", profile.chocolate());
        profileData.set(base + ".chocolate-factory.all-time", profile.allTimeChocolate());
        profileData.set(base + ".chocolate-factory.this-prestige", profile.chocolateThisPrestige());
        profileData.set(base + ".chocolate-factory.spent", profile.chocolateSpent());
        profileData.set(base + ".chocolate-factory.last-accrual-millis", profile.chocolateLastAccrualMillis());
        profileData.set(base + ".chocolate-factory.factory-level", profile.chocolateFactoryLevel());
        profileData.set(base + ".chocolate-factory.hoppity-eggs", profile.hoppityEggsFound());
        profileData.set(base + ".chocolate-factory.hoppity-egg-claim-key", profile.hoppityEggClaimKey());
        profileData.set(base + ".chocolate-factory.hoppity-shop-year", profile.hoppityShopYear());
        profileData.set(base + ".chocolate-factory.hoppity-shop-purchases", profile.hoppityShopPurchases().stream().sorted().toList());
        profileData.set(base + ".chocolate-factory.shop-year", profile.chocolateShopYear());
        for (Map.Entry<String, Integer> entry : profile.chocolateUpgrades().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".chocolate-factory.upgrades." + entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Integer> entry : profile.chocolateRabbits().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".chocolate-factory.rabbits." + entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Integer> entry : profile.chocolateShopPurchases().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".chocolate-factory.shop-purchases." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".mining-fiesta", null);
        profileData.set(base + ".mining-fiesta.blocks", profile.miningFiestaBlocks());
        profileData.set(base + ".mining-fiesta.refined-minerals", profile.miningFiestaRefinedMinerals());
        profileData.set(base + ".mining-fiesta.glossy-gemstones", profile.miningFiestaGlossyGemstones());
        for (Map.Entry<String, Long> entry : profile.miningFiestaBuffs().entrySet()) {
            if (entry.getValue() > System.currentTimeMillis()) {
                profileData.set(base + ".mining-fiesta.buffs." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".fishing-festival", null);
        profileData.set(base + ".fishing-festival.shark-catches", profile.fishingFestivalSharkCatches());
        for (Map.Entry<String, Long> entry : profile.fishingFestivalSharks().entrySet()) {
            if (entry.getValue() > 0L) {
                profileData.set(base + ".fishing-festival.sharks." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".shop-purchases.day", profile.shopPurchaseDay());
        profileData.set(base + ".shop-purchases.items", null);
        for (Map.Entry<String, Integer> entry : profile.dailyShopPurchases().entrySet()) {
            profileData.set(base + ".shop-purchases.items." + entry.getKey(), entry.getValue());
        }
        profileData.set(base + ".accessory-bag", profile.accessoryBag());
        profileData.set(base + ".tuning", null);
        for (Map.Entry<String, Integer> entry : profile.tuning().entrySet()) {
            profileData.set(base + ".tuning." + entry.getKey(), entry.getValue());
        }
        profileData.set(base + ".equipment", null);
        for (Map.Entry<String, ItemStack> entry : profile.equipment().entrySet()) {
            profileData.set(base + ".equipment." + entry.getKey(), entry.getValue());
        }
        profileData.set(base + ".wardrobe", null);
        for (Map.Entry<Integer, WardrobeSet> entry : profile.wardrobe().entrySet()) {
            WardrobeSet set = entry.getValue();
            if (set == null || set.empty()) {
                continue;
            }
            String wardrobeBase = base + ".wardrobe." + entry.getKey();
            profileData.set(wardrobeBase + ".helmet", set.helmet());
            profileData.set(wardrobeBase + ".chestplate", set.chestplate());
            profileData.set(wardrobeBase + ".leggings", set.leggings());
            profileData.set(wardrobeBase + ".boots", set.boots());
        }
        profileData.set(base + ".storage", null);
        for (Map.Entry<Integer, ItemStack[]> entry : profile.storagePages().entrySet()) {
            ItemStack[] contents = entry.getValue();
            if (contents == null) {
                continue;
            }
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack itemStack = contents[slot];
                if (itemStack != null && !itemStack.getType().isAir()) {
                    profileData.set(base + ".storage.pages." + entry.getKey() + "." + slot, itemStack);
                }
            }
        }
        profileData.set(base + ".backpacks", null);
        for (Map.Entry<Integer, OwnedBackpack> entry : profile.backpacks().entrySet()) {
            OwnedBackpack backpack = entry.getValue();
            if (backpack == null) {
                continue;
            }
            String backpackBase = base + ".backpacks." + entry.getKey();
            profileData.set(backpackBase + ".id", backpack.id());
            ItemStack[] contents = backpack.contents();
            for (int slot = 0; slot < contents.length; slot++) {
                ItemStack itemStack = contents[slot];
                if (itemStack != null && !itemStack.getType().isAir()) {
                    profileData.set(backpackBase + ".items." + slot, itemStack);
                }
            }
        }
        profileData.set(base + ".sacks", null);
        for (Map.Entry<String, Map<String, Long>> sackEntry : profile.sacks().entrySet()) {
            for (Map.Entry<String, Long> itemEntry : sackEntry.getValue().entrySet()) {
                if (itemEntry.getValue() > 0L) {
                    profileData.set(base + ".sacks." + sackEntry.getKey() + "." + itemEntry.getKey(), itemEntry.getValue());
                }
            }
        }
        profileData.set(base + ".quiver.selected", profile.selectedQuiverItem());
        profileData.set(base + ".quiver.items", null);
        for (Map.Entry<String, Long> entry : profile.quiver().entrySet()) {
            if (entry.getValue() > 0L) {
                profileData.set(base + ".quiver.items." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".potions.active", null);
        for (Map.Entry<String, Long> entry : profile.potionEffects().entrySet()) {
            if (entry.getValue() > 0L) {
                profileData.set(base + ".potions.active." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".cakes.active", null);
        for (Map.Entry<String, Long> entry : profile.cakeBuffs().entrySet()) {
            if (entry.getValue() > System.currentTimeMillis()) {
                profileData.set(base + ".cakes.active." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".cakes.placed", null);
        for (int index = 0; index < profile.placedCakes().size(); index++) {
            PlacedCake cake = profile.placedCakes().get(index);
            String cakeBase = base + ".cakes.placed." + index;
            profileData.set(cakeBase + ".id", cake.id());
            profileData.set(cakeBase + ".world", cake.worldName());
            profileData.set(cakeBase + ".x", cake.x());
            profileData.set(cakeBase + ".y", cake.y());
            profileData.set(cakeBase + ".z", cake.z());
        }
        profileData.set(base + ".upgrades", null);
        for (Map.Entry<String, Integer> entry : profile.upgrades().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".upgrades." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".essence", null);
        for (Map.Entry<String, Double> entry : profile.essence().entrySet()) {
            if (entry.getValue() > 0.0D) {
                profileData.set(base + ".essence." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".bestiary", null);
        for (Map.Entry<String, Long> entry : profile.bestiaryKills().entrySet()) {
            if (entry.getValue() > 0L) {
                profileData.set(base + ".bestiary.kills." + entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Integer> entry : profile.bestiaryTiers().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".bestiary.tiers." + entry.getKey(), entry.getValue());
            }
        }
        profileData.set(base + ".museum", null);
        for (Map.Entry<String, Long> entry : profile.museumDonations().entrySet()) {
            profileData.set(base + ".museum.donations." + entry.getKey(), entry.getValue());
        }
        profileData.set(base + ".dark-auction", null);
        for (Map.Entry<String, Integer> entry : profile.darkAuctionPurchases().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".dark-auction.purchases." + entry.getKey(), entry.getValue());
            }
        }
        for (int index = 0; index < profile.darkAuctionClaims().size(); index++) {
            ItemStack itemStack = profile.darkAuctionClaims().get(index);
            if (itemStack != null && !itemStack.getType().isAir()) {
                profileData.set(base + ".dark-auction.claims." + index, itemStack);
            }
        }
        profileData.set(base + ".mayors", null);
        for (Map.Entry<String, String> entry : profile.mayorVotes().entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                profileData.set(base + ".mayors.votes." + entry.getKey(), entry.getValue());
            }
        }
        if (!profile.claimedMayorBribes().isEmpty()) {
            profileData.set(base + ".mayors.claimed-bribes", profile.claimedMayorBribes().stream().sorted().toList());
        }
        profileData.set(base + ".farming-contests", null);
        profileData.set(base + ".farming-contests.jacobs-tickets", profile.jacobsTickets());
        for (Map.Entry<String, Long> entry : profile.farmingContestMedals().entrySet()) {
            if (entry.getValue() > 0L) {
                profileData.set(base + ".farming-contests.medals." + entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Map<String, Long>> contestEntry : profile.farmingContestScores().entrySet()) {
            for (Map.Entry<String, Long> scoreEntry : contestEntry.getValue().entrySet()) {
                if (scoreEntry.getValue() > 0L) {
                    profileData.set(base + ".farming-contests.scores." + contestEntry.getKey() + "." + scoreEntry.getKey(), scoreEntry.getValue());
                }
            }
        }
        for (Map.Entry<String, String> entry : profile.farmingContestRewards().entrySet()) {
            profileData.set(base + ".farming-contests.rewards." + entry.getKey(), entry.getValue());
        }
        profileData.set(base + ".commissions", null);
        profileData.set(base + ".commissions.hotm-xp", profile.hotmXp());
        profileData.set(base + ".commissions.total", profile.totalCommissions());
        profileData.set(base + ".commissions.daily.day", profile.commissionDay());
        profileData.set(base + ".commissions.daily.completed", profile.dailyCommissions());
        for (Map.Entry<String, Double> entry : profile.hotmPowder().entrySet()) {
            if (entry.getValue() > 0.0D) {
                profileData.set(base + ".commissions.powder." + entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<Integer, ActiveCommission> entry : profile.activeCommissions().entrySet()) {
            ActiveCommission commission = entry.getValue();
            if (commission == null) {
                continue;
            }
            String commissionBase = base + ".commissions.active." + entry.getKey();
            profileData.set(commissionBase + ".id", commission.id());
            profileData.set(commissionBase + ".progress", commission.progress());
        }
        profileData.set(base + ".forge", null);
        for (Map.Entry<Integer, ActiveForgeJob> entry : profile.forgeJobs().entrySet()) {
            ActiveForgeJob job = entry.getValue();
            if (job == null) {
                continue;
            }
            String forgeBase = base + ".forge.jobs." + entry.getKey();
            profileData.set(forgeBase + ".recipe", job.recipeId());
            profileData.set(forgeBase + ".started-at-millis", job.startedAtMillis());
            profileData.set(forgeBase + ".duration-millis", job.durationMillis());
        }
        profileData.set(base + ".cookies", null);
        profileData.set(base + ".cookies.buff-expires-at", profile.cookieBuffExpiresAtMillis());
        profileData.set(base + ".cookies.bits", profile.bits());
        profileData.set(base + ".cookies.bits-available", profile.bitsAvailable());
        profileData.set(base + ".cookies.fame-xp", profile.fameXp());
        profileData.set(base + ".cookies.consumed", profile.cookiesConsumed());
        profileData.set(base + ".cookies.last-accrual-millis", profile.bitsLastAccrualMillis());
        profileData.set(base + ".slayers", null);
        for (Map.Entry<String, Double> entry : profile.slayerXp().entrySet()) {
            if (entry.getValue() > 0.0D) {
                profileData.set(base + ".slayers.xp." + entry.getKey(), entry.getValue());
            }
        }
        for (Map.Entry<String, Integer> entry : profile.slayerLevels().entrySet()) {
            if (entry.getValue() > 0) {
                profileData.set(base + ".slayers.levels." + entry.getKey(), entry.getValue());
            }
        }
        ActiveSlayerQuest activeSlayer = profile.activeSlayer();
        if (activeSlayer != null) {
            profileData.set(base + ".slayers.active.id", activeSlayer.slayerId());
            profileData.set(base + ".slayers.active.tier", activeSlayer.tier());
            profileData.set(base + ".slayers.active.progress-xp", activeSlayer.progressXp());
            profileData.set(base + ".slayers.active.boss-spawned", activeSlayer.bossSpawned());
            if (activeSlayer.bossEntityId() != null) {
                profileData.set(base + ".slayers.active.boss-entity-id", activeSlayer.bossEntityId().toString());
            }
            if (activeSlayer.bossExpiresAtMillis() > 0L) {
                profileData.set(base + ".slayers.active.boss-expires-at", activeSlayer.bossExpiresAtMillis());
            }
        }
        profileData.set(base + ".pets.active", profile.activePetInstanceId());
        profileData.set(base + ".pets.owned", null);
        for (int index = 0; index < profile.pets().size(); index++) {
            OwnedPet pet = profile.pets().get(index);
            String petBase = base + ".pets.owned." + index;
            profileData.set(petBase + ".instance-id", pet.instanceId());
            profileData.set(petBase + ".id", pet.petId());
            profileData.set(petBase + ".xp", pet.xp());
            if (!pet.petItemId().isBlank()) {
                profileData.set(petBase + ".pet-item", pet.petItemId());
            }
        }
        profileData.set(base + ".pets.autopet", null);
        for (int index = 0; index < profile.autoPetRules().size(); index++) {
            AutoPetRule rule = profile.autoPetRules().get(index);
            String ruleBase = base + ".pets.autopet." + index;
            profileData.set(ruleBase + ".trigger", rule.trigger().key());
            profileData.set(ruleBase + ".pet", rule.petId());
        }
        profileData.set(base + ".minions", null);
        for (int index = 0; index < profile.minions().size(); index++) {
            PlacedMinion minion = profile.minions().get(index);
            String minionBase = base + ".minions." + index;
            profileData.set(minionBase + ".id", minion.id());
            profileData.set(minionBase + ".generated", minion.generatedAmount());
            profileData.set(minionBase + ".last-action-millis", minion.lastActionMillis());
            profileData.set(minionBase + ".world", minion.worldName());
            profileData.set(minionBase + ".x", minion.x());
            profileData.set(minionBase + ".y", minion.y());
            profileData.set(minionBase + ".z", minion.z());
            profileData.set(minionBase + ".fuel", null);
            if (!minion.fuelId().isBlank()) {
                profileData.set(minionBase + ".fuel.id", minion.fuelId());
                profileData.set(minionBase + ".fuel.expires-at-millis", minion.fuelExpiresAtMillis());
            }
            profileData.set(minionBase + ".upgrades", minion.upgradeIds().stream()
                    .filter(upgradeId -> upgradeId != null && !upgradeId.isBlank())
                    .map(upgradeId -> upgradeId.toUpperCase(Locale.ROOT))
                    .toList());
            profileData.set(minionBase + ".storage", null);
            if (minion.hasStorage()) {
                profileData.set(minionBase + ".storage.id", minion.storageId().toUpperCase(Locale.ROOT));
                profileData.set(minionBase + ".storage.world", minion.storageWorldName());
                profileData.set(minionBase + ".storage.x", minion.storageX());
                profileData.set(minionBase + ".storage.y", minion.storageY());
                profileData.set(minionBase + ".storage.z", minion.storageZ());
            }
            profileData.set(minionBase + ".skin", minion.hasSkin() ? minion.skinId().toUpperCase(Locale.ROOT) : null);
            profileData.set(minionBase + ".sold-coins", minion.soldCoins());
        }
    }

    private void saveFile() {
        try {
            profileData.save(profileFile);
        } catch (IOException exception) {
            plugin.getLogger().severe("Unable to save profiles.yml: " + exception.getMessage());
        }
    }
}
