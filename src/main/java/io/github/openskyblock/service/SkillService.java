package io.github.openskyblock.service;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.chocolate.ChocolateFactoryService;
import io.github.openskyblock.dojo.DojoService;
import io.github.openskyblock.dragon.DragonService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.faction.FactionService;
import io.github.openskyblock.fairysoul.FairySoulService;
import io.github.openskyblock.fishingfestival.FishingFestivalService;
import io.github.openskyblock.garden.GardenService;
import io.github.openskyblock.jerry.SeasonOfJerryService;
import io.github.openskyblock.kuudra.KuudraService;
import io.github.openskyblock.mayor.MayorService;
import io.github.openskyblock.museum.MuseumService;
import io.github.openskyblock.miningfiesta.MiningFiestaService;
import io.github.openskyblock.mythological.MythologicalService;
import io.github.openskyblock.newyear.NewYearService;
import io.github.openskyblock.pet.PetService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.rift.RiftService;
import io.github.openskyblock.spooky.SpookyService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

public final class SkillService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CollectionService collections;
    private final EconomyService economy;
    private final Map<SkillType, SkillDefinition> definitions = new EnumMap<>(SkillType.class);
    private final Map<Integer, Double> levelXp = new HashMap<>();
    private final Map<Material, ActionReward> blockRewards = new HashMap<>();
    private final Map<EntityType, ActionReward> entityRewards = new HashMap<>();
    private final Map<Material, ActionReward> pickupRewards = new HashMap<>();
    private MuseumService museumService;
    private FairySoulService fairySoulService;
    private GardenService gardenService;
    private DragonService dragonService;
    private RiftService riftService;
    private KuudraService kuudraService;
    private FactionService factionService;
    private DojoService dojoService;
    private MythologicalService mythologicalService;
    private SpookyService spookyService;
    private PetService petService;
    private SeasonOfJerryService seasonOfJerryService;
    private NewYearService newYearService;
    private ChocolateFactoryService chocolateFactoryService;
    private MiningFiestaService miningFiestaService;
    private FishingFestivalService fishingFestivalService;
    private MayorService mayorService;

    public SkillService(ConfigService configService, TextService text, ProfileManager profiles, CollectionService collections, EconomyService economy) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.collections = collections;
        this.economy = economy;
    }

    public void museumService(MuseumService museumService) {
        this.museumService = museumService;
    }

    public void fairySoulService(FairySoulService fairySoulService) {
        this.fairySoulService = fairySoulService;
    }

    public void gardenService(GardenService gardenService) {
        this.gardenService = gardenService;
    }

    public void dragonService(DragonService dragonService) {
        this.dragonService = dragonService;
    }

    public void riftService(RiftService riftService) {
        this.riftService = riftService;
    }

    public void kuudraService(KuudraService kuudraService) {
        this.kuudraService = kuudraService;
    }

    public void factionService(FactionService factionService) {
        this.factionService = factionService;
    }

    public void dojoService(DojoService dojoService) {
        this.dojoService = dojoService;
    }

    public void mythologicalService(MythologicalService mythologicalService) {
        this.mythologicalService = mythologicalService;
    }

    public void spookyService(SpookyService spookyService) {
        this.spookyService = spookyService;
    }

    public void petService(PetService petService) {
        this.petService = petService;
    }

    public void seasonOfJerryService(SeasonOfJerryService seasonOfJerryService) {
        this.seasonOfJerryService = seasonOfJerryService;
    }

    public void newYearService(NewYearService newYearService) {
        this.newYearService = newYearService;
    }

    public void chocolateFactoryService(ChocolateFactoryService chocolateFactoryService) {
        this.chocolateFactoryService = chocolateFactoryService;
    }

    public void miningFiestaService(MiningFiestaService miningFiestaService) {
        this.miningFiestaService = miningFiestaService;
    }

    public void fishingFestivalService(FishingFestivalService fishingFestivalService) {
        this.fishingFestivalService = fishingFestivalService;
    }

    public void mayorService(MayorService mayorService) {
        this.mayorService = mayorService;
    }

    public void reload() {
        definitions.clear();
        levelXp.clear();
        blockRewards.clear();
        entityRewards.clear();
        pickupRewards.clear();

        ConfigurationSection levelSection = configService.skills().getConfigurationSection("level-xp");
        if (levelSection != null) {
            for (String key : levelSection.getKeys(false)) {
                try {
                    levelXp.put(Integer.parseInt(key), levelSection.getDouble(key));
                } catch (NumberFormatException ignored) {
                    // Invalid level keys are ignored so server owners can fix config without crashing startup.
                }
            }
        }

        ConfigurationSection skillSection = configService.skills().getConfigurationSection("skills");
        if (skillSection != null) {
            for (String key : skillSection.getKeys(false)) {
                SkillType.fromKey(key).ifPresent(type -> definitions.put(type, new SkillDefinition(
                        type,
                        skillSection.getString(key + ".display-name", type.name()),
                        skillSection.getInt(key + ".max-level", 50)
                )));
            }
        }
        for (SkillType type : SkillType.values()) {
            definitions.putIfAbsent(type, new SkillDefinition(type, type.name(), 50));
        }

        loadMaterialRewards("actions.block-break", blockRewards);
        loadMaterialRewards("actions.item-pickup", pickupRewards);
        loadEntityRewards();
    }

    public Optional<ActionReward> blockReward(Material material) {
        return Optional.ofNullable(blockRewards.get(material));
    }

    public Optional<ActionReward> pickupReward(Material material) {
        return Optional.ofNullable(pickupRewards.get(material));
    }

    public void grantPickupReward(Player player, Material material, long amount) {
        pickupReward(material).ifPresent(reward -> {
            long scaledAmount = Math.max(1L, amount);
            grantActionReward(player, new ActionReward(
                    reward.skillType(),
                    reward.skillXp() * scaledAmount,
                    reward.collectionId(),
                    reward.collectionAmount() * scaledAmount,
                    reward.coins() * scaledAmount
            ));
        });
    }

    public Optional<ActionReward> entityReward(EntityType entityType) {
        return Optional.ofNullable(entityRewards.get(entityType));
    }

    public void grantActionReward(Player player, ActionReward reward) {
        if (reward.skillType() != null && reward.skillXp() > 0.0D) {
            addXp(player, reward.skillType(), reward.skillXp());
        }
        if (reward.collectionId() != null && !reward.collectionId().isBlank() && reward.collectionAmount() > 0L) {
            collections.addProgress(player, reward.collectionId(), reward.collectionAmount());
        }
        if (reward.coins() > 0.0D) {
            economy.addPurse(player, reward.coins());
            text.send(player, "progression.coins", List.of(TextService.raw("coins", text.formatNumber(reward.coins()))));
        }
    }

    public void addXp(Player player, SkillType skillType, double amount) {
        if (!configService.main().getBoolean("features.skills", true)) {
            return;
        }
        double effectiveAmount = effectiveSkillXp(skillType, amount);
        SkyBlockProfile profile = profiles.profile(player);
        int before = level(skillType, profile.skillXp(skillType));
        profile.addSkillXp(skillType, effectiveAmount);
        int after = level(skillType, profile.skillXp(skillType));
        SkillDefinition definition = definition(skillType);
        text.send(player, "progression.skill-xp", List.of(
                TextService.raw("xp", text.formatNumber(effectiveAmount)),
                TextService.parsed("skill", definition.displayName())
        ));
        if (after > before) {
            text.send(player, "progression.skill-level-up", List.of(
                    TextService.parsed("skill", definition.displayName()),
                    TextService.raw("level", Integer.toString(after))
            ));
        }
    }

    public int level(SkillType skillType, double xp) {
        SkillDefinition definition = definition(skillType);
        int level = 0;
        for (int nextLevel : sortedLevels()) {
            if (nextLevel > definition.maxLevel()) {
                break;
            }
            double required = xpForLevel(nextLevel);
            if (xp < required) {
                break;
            }
            xp -= required;
            level = nextLevel;
        }
        return Math.min(level, definition.maxLevel());
    }

    public double xpIntoCurrentLevel(SkillType skillType, double xp) {
        int currentLevel = level(skillType, xp);
        double remaining = Math.max(0.0D, xp);
        for (int nextLevel : sortedLevels()) {
            if (nextLevel > currentLevel) {
                break;
            }
            remaining -= xpForLevel(nextLevel);
        }
        return Math.max(0.0D, remaining);
    }

    public double requiredXpForNextLevel(SkillType skillType, double xp) {
        SkillDefinition definition = definition(skillType);
        int currentLevel = level(skillType, xp);
        if (currentLevel >= definition.maxLevel()) {
            return 0.0D;
        }
        return xpForLevel(currentLevel + 1);
    }

    public double progressToNextLevel(SkillType skillType, double xp) {
        double required = requiredXpForNextLevel(skillType, xp);
        if (required <= 0.0D) {
            return 100.0D;
        }
        return Math.max(0.0D, Math.min(100.0D, xpIntoCurrentLevel(skillType, xp) / required * 100.0D));
    }

    public SkillDefinition definition(SkillType skillType) {
        return definitions.getOrDefault(skillType, new SkillDefinition(skillType, skillType.name(), 50));
    }

    public List<SkillDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparingInt(definition -> definition.type().ordinal()))
                .toList();
    }

    public int skyBlockLevel(SkyBlockProfile profile) {
        int xp = 0;
        for (SkillType type : SkillType.values()) {
            xp += level(type, profile.skillXp(type)) * 5;
        }
        xp += profile.collections().values().stream().mapToInt(value -> (int) Math.min(25L, value / 100L)).sum();
        if (museumService != null) {
            xp += (int) Math.round(museumService.skyBlockXp(profile));
        }
        if (fairySoulService != null) {
            xp += (int) Math.round(fairySoulService.skyBlockXp(profile));
        }
        if (gardenService != null) {
            xp += (int) Math.round(gardenService.skyBlockXp(profile));
        }
        if (dragonService != null) {
            xp += (int) Math.round(dragonService.skyBlockXp(profile));
        }
        if (riftService != null) {
            xp += (int) Math.round(riftService.skyBlockXp(profile));
        }
        if (kuudraService != null) {
            xp += (int) Math.round(kuudraService.skyBlockXp(profile));
        }
        if (factionService != null) {
            xp += (int) Math.round(factionService.skyBlockXp(profile));
        }
        if (dojoService != null) {
            xp += (int) Math.round(dojoService.skyBlockXp(profile));
        }
        if (mythologicalService != null) {
            xp += (int) Math.round(mythologicalService.skyBlockXp(profile));
        }
        if (spookyService != null) {
            xp += (int) Math.round(spookyService.skyBlockXp(profile));
        }
        if (petService != null) {
            xp += (int) Math.round(petService.scoreSkyBlockXp(profile));
        }
        if (seasonOfJerryService != null) {
            xp += (int) Math.round(seasonOfJerryService.skyBlockXp(profile));
        }
        if (newYearService != null) {
            xp += (int) Math.round(newYearService.skyBlockXp(profile));
        }
        if (chocolateFactoryService != null) {
            xp += (int) Math.round(chocolateFactoryService.skyBlockXp(profile));
        }
        if (miningFiestaService != null) {
            xp += (int) Math.round(miningFiestaService.skyBlockXp(profile));
        }
        if (fishingFestivalService != null) {
            xp += (int) Math.round(fishingFestivalService.skyBlockXp(profile));
        }
        int xpPerLevel = Math.max(1, configService.main().getInt("settings.skyblock-level-xp-per-level", 100));
        return Math.max(1, xp / xpPerLevel + 1);
    }

    private double effectiveSkillXp(SkillType skillType, double amount) {
        if (amount <= 0.0D || mayorService == null) {
            return Math.max(0.0D, amount);
        }
        String key = skillType.name().toLowerCase(Locale.ROOT);
        double multiplier = Math.max(0.0D, mayorService.modifier("global_skill_xp_multiplier"));
        multiplier += Math.max(0.0D, mayorService.modifier(key + "_xp_multiplier"));
        multiplier += Math.max(0.0D, mayorService.modifier(key + "_wisdom_bonus")) / 100.0D;
        return Math.max(0.0D, amount * (1.0D + multiplier));
    }

    private void loadMaterialRewards(String path, Map<Material, ActionReward> target) {
        ConfigurationSection section = configService.skills().getConfigurationSection(path);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                continue;
            }
            ActionReward reward = readReward(section.getConfigurationSection(key));
            if (reward != null) {
                target.put(material, reward);
            }
        }
    }

    private void loadEntityRewards() {
        ConfigurationSection section = configService.skills().getConfigurationSection("actions.entity-kill");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                EntityType entityType = EntityType.valueOf(key.toUpperCase(Locale.ROOT));
                ActionReward reward = readReward(section.getConfigurationSection(key));
                if (reward != null) {
                    entityRewards.put(entityType, reward);
                }
            } catch (IllegalArgumentException ignored) {
                // Invalid entity keys are skipped.
            }
        }
    }

    private ActionReward readReward(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        SkillType skillType = SkillType.fromKey(section.getString("skill", "")).orElse(null);
        double xp = section.getDouble("xp", 0.0D);
        double coins = section.getDouble("coins", 0.0D);
        String collectionId = section.getString("collection", "");
        long collectionAmount = section.getLong("collection-amount", 0L);
        if (collectionId != null && !collectionId.isBlank()) {
            collectionId = collectionId.toUpperCase(Locale.ROOT);
        }
        return new ActionReward(skillType, xp, collectionId, collectionAmount, coins);
    }

    private List<Integer> sortedLevels() {
        int maxConfiguredLevel = definitions.values().stream()
                .mapToInt(SkillDefinition::maxLevel)
                .max()
                .orElse(60);
        int maxLevel = Math.max(maxConfiguredLevel, levelXp.keySet().stream().mapToInt(Integer::intValue).max().orElse(0));
        List<Integer> levels = new ArrayList<>();
        for (int level = 1; level <= maxLevel; level++) {
            levels.add(level);
        }
        return levels;
    }

    private double xpForLevel(int level) {
        return levelXp.getOrDefault(level, defaultXpForLevel(level));
    }

    private double defaultXpForLevel(int level) {
        return 50.0D * level * level;
    }
}
