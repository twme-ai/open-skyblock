package io.github.openskyblock.garden;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CollectionService;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.service.SkillService;
import io.github.openskyblock.service.SkillType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class GardenService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final SkillService skills;
    private final CollectionService collections;
    private final CustomItemService customItems;
    private final Map<String, GardenCropDefinition> crops = new HashMap<>();
    private final Map<String, GardenPlotDefinition> plots = new HashMap<>();
    private final Map<String, GardenVisitorDefinition> visitors = new HashMap<>();
    private final Map<Integer, Double> gardenLevels = new HashMap<>();
    private long cropsPerCompost = 160L;
    private double skyBlockXpPerGardenLevel = 3.0D;
    private double skyBlockXpPerCropMilestone = 0.5D;
    private double skyBlockXpPerUniqueVisitor = 0.25D;

    public GardenService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, SkillService skills, CollectionService collections, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.skills = skills;
        this.collections = collections;
        this.customItems = customItems;
    }

    public void reload() {
        cropsPerCompost = Math.max(1L, configService.garden().getLong("settings.crops-per-compost", 160L));
        skyBlockXpPerGardenLevel = Math.max(0.0D, configService.garden().getDouble("settings.skyblock-xp-per-garden-level", 3.0D));
        skyBlockXpPerCropMilestone = Math.max(0.0D, configService.garden().getDouble("settings.skyblock-xp-per-crop-milestone", 0.5D));
        skyBlockXpPerUniqueVisitor = Math.max(0.0D, configService.garden().getDouble("settings.skyblock-xp-per-unique-visitor", 0.25D));
        loadLevels();
        loadCrops();
        loadPlots();
        loadVisitors();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.garden", true);
    }

    public List<String> cropIds() {
        return cropDefinitions().stream().map(GardenCropDefinition::id).toList();
    }

    public List<String> plotIds() {
        return plotDefinitions().stream().map(GardenPlotDefinition::id).toList();
    }

    public List<String> visitorIds() {
        return visitorDefinitions().stream().map(GardenVisitorDefinition::id).toList();
    }

    public Optional<GardenCropDefinition> crop(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(crops.get(id.toUpperCase(Locale.ROOT)));
    }

    public Optional<GardenPlotDefinition> plot(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(plots.get(id.toUpperCase(Locale.ROOT)));
    }

    public Optional<GardenVisitorDefinition> visitor(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(visitors.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<GardenCropDefinition> cropDefinitions() {
        return crops.values().stream()
                .sorted(Comparator.comparing(GardenCropDefinition::id))
                .toList();
    }

    public List<GardenPlotDefinition> plotDefinitions() {
        return plots.values().stream()
                .sorted(Comparator.comparingInt(GardenPlotDefinition::requiredGardenLevel).thenComparing(GardenPlotDefinition::id))
                .toList();
    }

    public List<GardenVisitorDefinition> visitorDefinitions() {
        return visitors.values().stream()
                .sorted(Comparator.comparingInt(GardenVisitorDefinition::requiredGardenLevel).thenComparing(GardenVisitorDefinition::id))
                .toList();
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.garden-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        ensureDefaultPlots(profile);
        text.send(player, "commands.garden-status", List.of(
                TextService.raw("level", Integer.toString(gardenLevel(profile))),
                TextService.raw("xp", text.formatNumber(profile.gardenXp())),
                TextService.raw("next_xp", text.formatNumber(nextLevelXp(profile))),
                TextService.raw("copper", text.formatNumber(profile.gardenCopper())),
                TextService.raw("compost", text.formatNumber(profile.gardenCompost())),
                TextService.raw("plots", Integer.toString(profile.gardenPlots().size())),
                TextService.raw("total_plots", Integer.toString(plots.size())),
                TextService.raw("visitors", text.formatNumber(profile.gardenVisitorOffers())),
                TextService.raw("unique_visitors", Integer.toString(profile.gardenVisitorsServed().size())),
                TextService.raw("crop_milestones", Integer.toString(totalCropMilestones(profile))),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile)))
        ));
    }

    public void sendCrops(Player player) {
        if (!enabled()) {
            text.send(player, "commands.garden-disabled");
            return;
        }
        if (crops.isEmpty()) {
            text.send(player, "commands.garden-crop-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.garden-crop-header");
        for (GardenCropDefinition crop : cropDefinitions()) {
            text.send(player, "commands.garden-crop-line", cropPlaceholders(profile, crop));
        }
    }

    public void sendPlots(Player player) {
        if (!enabled()) {
            text.send(player, "commands.garden-disabled");
            return;
        }
        if (plots.isEmpty()) {
            text.send(player, "commands.garden-plot-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        ensureDefaultPlots(profile);
        text.send(player, "commands.garden-plot-header");
        for (GardenPlotDefinition plot : plotDefinitions()) {
            text.send(player, "commands.garden-plot-line", plotPlaceholders(profile, plot));
        }
    }

    public void sendVisitors(Player player) {
        if (!enabled()) {
            text.send(player, "commands.garden-disabled");
            return;
        }
        if (visitors.isEmpty()) {
            text.send(player, "commands.garden-visitor-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.garden-visitor-header");
        for (GardenVisitorDefinition visitor : visitorDefinitions()) {
            text.send(player, "commands.garden-visitor-line", visitorPlaceholders(profile, visitor));
        }
    }

    public boolean harvest(Player player, String cropId, long amount) {
        if (!enabled()) {
            text.send(player, "commands.garden-disabled");
            return false;
        }
        GardenCropDefinition crop = crop(cropId).orElse(null);
        if (crop == null) {
            text.send(player, "commands.garden-unknown-crop", List.of(TextService.raw("crop", cropId == null ? "" : cropId)));
            return false;
        }
        if (amount <= 0L) {
            text.send(player, "errors.invalid-number");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        long beforeHarvests = profile.gardenCropHarvests(crop.id());
        int beforeMilestone = cropMilestone(crop, beforeHarvests);
        profile.addGardenCropHarvests(crop.id(), amount);
        profile.addGardenCropStorage(crop.id(), amount);
        long afterHarvests = profile.gardenCropHarvests(crop.id());
        int afterMilestone = cropMilestone(crop, afterHarvests);
        int gainedMilestones = Math.max(0, afterMilestone - beforeMilestone);
        double gardenXp = gainedMilestones * crop.gardenXpPerMilestone();
        long copper = gainedMilestones * crop.copperPerMilestone();
        if (gardenXp > 0.0D) {
            profile.addGardenXp(gardenXp);
        }
        if (copper > 0L) {
            profile.addGardenCopper(copper);
        }
        if (crop.farmingXpPerHarvest() > 0.0D) {
            skills.addXp(player, SkillType.FARMING, crop.farmingXpPerHarvest() * amount);
        }
        if (!crop.collectionId().isBlank()) {
            collections.addProgress(player, crop.collectionId(), amount);
        }
        profiles.save(player);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(cropPlaceholders(profile, crop));
        placeholders.add(TextService.raw("amount", text.formatNumber(amount)));
        placeholders.add(TextService.raw("milestones_gained", Integer.toString(gainedMilestones)));
        placeholders.add(TextService.raw("garden_xp_gained", text.formatNumber(gardenXp)));
        placeholders.add(TextService.raw("copper_gained", text.formatNumber(copper)));
        text.send(player, "commands.garden-harvested", placeholders);
        return true;
    }

    public boolean compost(Player player, String cropId, long amount) {
        if (!enabled()) {
            text.send(player, "commands.garden-disabled");
            return false;
        }
        GardenCropDefinition crop = crop(cropId).orElse(null);
        if (crop == null) {
            text.send(player, "commands.garden-unknown-crop", List.of(TextService.raw("crop", cropId == null ? "" : cropId)));
            return false;
        }
        if (amount <= 0L) {
            text.send(player, "errors.invalid-number");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.gardenCropStorage(crop.id()) < amount) {
            text.send(player, "commands.garden-not-enough-crops", cropPlaceholders(profile, crop));
            return false;
        }
        long compost = amount / cropsPerCompost;
        if (compost <= 0L) {
            text.send(player, "commands.garden-compost-too-small", List.of(TextService.raw("crops_per_compost", text.formatNumber(cropsPerCompost))));
            return false;
        }
        long consumed = compost * cropsPerCompost;
        profile.addGardenCropStorage(crop.id(), -consumed);
        profile.addGardenCompost(compost);
        profiles.save(player);
        text.send(player, "commands.garden-composted", List.of(
                TextService.raw("amount", text.formatNumber(consumed)),
                TextService.parsed("crop", crop.displayName()),
                TextService.raw("compost", text.formatNumber(compost)),
                TextService.raw("total_compost", text.formatNumber(profile.gardenCompost()))
        ));
        return true;
    }

    public boolean unlockPlot(Player player, String plotId) {
        if (!enabled()) {
            text.send(player, "commands.garden-disabled");
            return false;
        }
        GardenPlotDefinition plot = plot(plotId).orElse(null);
        if (plot == null) {
            text.send(player, "commands.garden-unknown-plot", List.of(TextService.raw("plot", plotId == null ? "" : plotId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        ensureDefaultPlots(profile);
        if (profile.hasGardenPlot(plot.id())) {
            text.send(player, "commands.garden-plot-already", plotPlaceholders(profile, plot));
            return false;
        }
        if (gardenLevel(profile) < plot.requiredGardenLevel()) {
            text.send(player, "commands.garden-plot-requires-level", plotPlaceholders(profile, plot));
            return false;
        }
        if (profile.gardenCompost() < plot.compostCost()) {
            text.send(player, "commands.garden-plot-no-compost", plotPlaceholders(profile, plot));
            return false;
        }
        if (!economy.spendPurse(player, plot.coinCost())) {
            text.send(player, "commands.garden-plot-no-money", plotPlaceholders(profile, plot));
            return false;
        }
        profile.addGardenCompost(-plot.compostCost());
        profile.addGardenPlot(plot.id());
        profiles.save(player);
        text.send(player, "commands.garden-plot-unlocked", plotPlaceholders(profile, plot));
        return true;
    }

    public boolean serveVisitor(Player player, String visitorId) {
        if (!enabled()) {
            text.send(player, "commands.garden-disabled");
            return false;
        }
        GardenVisitorDefinition visitor = visitor(visitorId).orElse(null);
        if (visitor == null) {
            text.send(player, "commands.garden-unknown-visitor", List.of(TextService.raw("visitor", visitorId == null ? "" : visitorId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (gardenLevel(profile) < visitor.requiredGardenLevel()) {
            text.send(player, "commands.garden-visitor-requires-level", visitorPlaceholders(profile, visitor));
            return false;
        }
        GardenCropDefinition crop = crop(visitor.requiredCropId()).orElse(null);
        if (crop == null || profile.gardenCropStorage(crop.id()) < visitor.requiredCropAmount()) {
            text.send(player, "commands.garden-visitor-missing-crops", visitorPlaceholders(profile, visitor));
            return false;
        }
        profile.addGardenCropStorage(crop.id(), -visitor.requiredCropAmount());
        profile.addGardenXp(visitor.gardenXp());
        profile.addGardenCopper(visitor.copper());
        profile.addGardenVisitorServed(visitor.id(), 1);
        if (visitor.farmingXp() > 0.0D) {
            skills.addXp(player, SkillType.FARMING, visitor.farmingXp());
        }
        if (visitor.coins() > 0.0D) {
            economy.addPurse(player, visitor.coins());
        }
        List<String> rewardDisplays = new ArrayList<>();
        selectReward(visitor.rewards()).flatMap(reward -> giveReward(player, reward)).ifPresent(rewardDisplays::add);
        profiles.save(player);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(visitorPlaceholders(profile, visitor));
        placeholders.add(TextService.parsed("rewards", rewardDisplays.isEmpty() ? "<gray>none</gray>" : String.join("<gray>, </gray>", rewardDisplays)));
        text.send(player, "commands.garden-visitor-served", placeholders);
        return true;
    }

    public int gardenLevel(SkyBlockProfile profile) {
        int level = 0;
        for (int candidate : gardenLevels.keySet().stream().sorted().toList()) {
            if (profile.gardenXp() >= gardenLevels.getOrDefault(candidate, 0.0D)) {
                level = Math.max(level, candidate);
            }
        }
        return level;
    }

    public double skyBlockXp(SkyBlockProfile profile) {
        return gardenLevel(profile) * skyBlockXpPerGardenLevel
                + totalCropMilestones(profile) * skyBlockXpPerCropMilestone
                + profile.gardenVisitorsServed().size() * skyBlockXpPerUniqueVisitor;
    }

    private void loadLevels() {
        gardenLevels.clear();
        ConfigurationSection section = configService.garden().getConfigurationSection("garden-levels");
        if (section == null) {
            gardenLevels.put(0, 0.0D);
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                gardenLevels.put(Integer.parseInt(key), Math.max(0.0D, section.getDouble(key + ".required-xp", 0.0D)));
            } catch (NumberFormatException ignored) {
                // Invalid level keys are ignored so the rest of the Garden config can still load.
            }
        }
        gardenLevels.putIfAbsent(0, 0.0D);
    }

    private void loadCrops() {
        crops.clear();
        ConfigurationSection section = configService.garden().getConfigurationSection("crops");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection cropSection = section.getConfigurationSection(id);
            if (cropSection == null) {
                continue;
            }
            Material material = Material.matchMaterial(cropSection.getString("material", "WHEAT"));
            String normalized = id.toUpperCase(Locale.ROOT);
            crops.put(normalized, new GardenCropDefinition(
                    normalized,
                    cropSection.getString("display-name", normalized),
                    material == null || material.isAir() ? Material.WHEAT : material,
                    cropSection.getString("collection", normalized).toUpperCase(Locale.ROOT),
                    Math.max(1L, cropSection.getLong("milestone-interval", 1000L)),
                    Math.max(0, cropSection.getInt("max-milestone", 10)),
                    Math.max(0.0D, cropSection.getDouble("farming-xp-per-harvest", 0.0D)),
                    Math.max(0.0D, cropSection.getDouble("garden-xp-per-milestone", 0.0D)),
                    Math.max(0L, cropSection.getLong("copper-per-milestone", 0L))
            ));
        }
    }

    private void loadPlots() {
        plots.clear();
        ConfigurationSection section = configService.garden().getConfigurationSection("plots");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection plotSection = section.getConfigurationSection(id);
            if (plotSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            plots.put(normalized, new GardenPlotDefinition(
                    normalized,
                    plotSection.getString("display-name", normalized),
                    Math.max(0, plotSection.getInt("required-garden-level", 0)),
                    Math.max(0L, plotSection.getLong("compost-cost", 0L)),
                    Math.max(0.0D, plotSection.getDouble("coin-cost", 0.0D)),
                    plotSection.getBoolean("unlocked-by-default", false)
            ));
        }
    }

    private void loadVisitors() {
        visitors.clear();
        ConfigurationSection section = configService.garden().getConfigurationSection("visitors");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection visitorSection = section.getConfigurationSection(id);
            if (visitorSection == null) {
                continue;
            }
            List<GardenRewardDefinition> rewards = new ArrayList<>();
            for (Map<?, ?> rawReward : visitorSection.getMapList("rewards")) {
                reward(rawReward).ifPresent(rewards::add);
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            visitors.put(normalized, new GardenVisitorDefinition(
                    normalized,
                    visitorSection.getString("display-name", normalized),
                    Math.max(0, visitorSection.getInt("required-garden-level", 0)),
                    visitorSection.getString("required.crop", "").toUpperCase(Locale.ROOT),
                    Math.max(0L, visitorSection.getLong("required.amount", 0L)),
                    Math.max(0.0D, visitorSection.getDouble("garden-xp", 0.0D)),
                    Math.max(0L, visitorSection.getLong("copper", 0L)),
                    Math.max(0.0D, visitorSection.getDouble("farming-xp", 0.0D)),
                    Math.max(0.0D, visitorSection.getDouble("coins", 0.0D)),
                    List.copyOf(rewards)
            ));
        }
    }

    private Optional<GardenRewardDefinition> reward(Map<?, ?> section) {
        String type = string(section.get("type"), "VANILLA").toUpperCase(Locale.ROOT);
        int amount = Math.max(1, integer(section.get("amount"), 1));
        double weight = Math.max(0.0D, decimal(section.get("weight"), 1.0D));
        if (weight <= 0.0D) {
            return Optional.empty();
        }
        if (type.equals("COINS")) {
            double coins = Math.max(0.0D, decimal(section.get("coins"), decimal(section.get("amount"), 0.0D)));
            return coins <= 0.0D ? Optional.empty() : Optional.of(new GardenRewardDefinition("COINS", "", Material.GOLD_NUGGET, 1, coins, weight));
        }
        if (type.equals("CUSTOM_ITEM")) {
            String itemId = string(section.get("item"), "").toUpperCase(Locale.ROOT);
            return itemId.isBlank() ? Optional.empty() : Optional.of(new GardenRewardDefinition("CUSTOM_ITEM", itemId, Material.STONE, amount, 0.0D, weight));
        }
        Material material = Material.matchMaterial(string(section.get("material"), "WHEAT"));
        if (material == null || material.isAir()) {
            return Optional.empty();
        }
        return Optional.of(new GardenRewardDefinition("VANILLA", "", material, amount, 0.0D, weight));
    }

    private Optional<GardenRewardDefinition> selectReward(List<GardenRewardDefinition> rewards) {
        double totalWeight = rewards.stream().mapToDouble(GardenRewardDefinition::weight).sum();
        if (totalWeight <= 0.0D) {
            return Optional.empty();
        }
        double selected = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0.0D;
        for (GardenRewardDefinition reward : rewards) {
            cumulative += reward.weight();
            if (selected <= cumulative) {
                return Optional.of(reward);
            }
        }
        return rewards.stream().filter(reward -> reward.weight() > 0.0D).findFirst();
    }

    private Optional<String> giveReward(Player player, GardenRewardDefinition reward) {
        if (reward.coinsReward()) {
            economy.addPurse(player, reward.coins());
            return Optional.of(rewardDisplay(reward));
        }
        ItemStack itemStack;
        if (reward.customItem()) {
            CustomItemDefinition definition = customItems.definition(reward.itemId()).orElse(null);
            if (definition == null) {
                return Optional.empty();
            }
            itemStack = customItems.createItem(definition);
        } else {
            itemStack = new ItemStack(reward.material());
        }
        itemStack.setAmount(reward.amount());
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        return Optional.of(rewardDisplay(reward));
    }

    private List<TextService.TextPlaceholder> cropPlaceholders(SkyBlockProfile profile, GardenCropDefinition crop) {
        long harvests = profile.gardenCropHarvests(crop.id());
        int milestone = cropMilestone(crop, harvests);
        return List.of(
                TextService.raw("id", crop.id()),
                TextService.parsed("crop", crop.displayName()),
                TextService.raw("harvests", text.formatNumber(harvests)),
                TextService.raw("stored", text.formatNumber(profile.gardenCropStorage(crop.id()))),
                TextService.raw("milestone", Integer.toString(milestone)),
                TextService.raw("max_milestone", Integer.toString(crop.maxMilestone())),
                TextService.raw("next_milestone", text.formatNumber(nextCropMilestone(crop, harvests))),
                TextService.raw("collection", crop.collectionId())
        );
    }

    private List<TextService.TextPlaceholder> plotPlaceholders(SkyBlockProfile profile, GardenPlotDefinition plot) {
        return List.of(
                TextService.raw("id", plot.id()),
                TextService.parsed("plot", plot.displayName()),
                TextService.raw("required_level", Integer.toString(plot.requiredGardenLevel())),
                TextService.raw("compost_cost", text.formatNumber(plot.compostCost())),
                TextService.raw("coin_cost", text.formatNumber(plot.coinCost())),
                TextService.parsed("status", profile.hasGardenPlot(plot.id()) ? "<green>Unlocked</green>" : "<red>Locked</red>")
        );
    }

    private List<TextService.TextPlaceholder> visitorPlaceholders(SkyBlockProfile profile, GardenVisitorDefinition visitor) {
        GardenCropDefinition crop = crop(visitor.requiredCropId()).orElse(null);
        String cropName = crop == null ? visitor.requiredCropId() : crop.displayName();
        long stored = crop == null ? 0L : profile.gardenCropStorage(crop.id());
        return List.of(
                TextService.raw("id", visitor.id()),
                TextService.parsed("visitor", visitor.displayName()),
                TextService.raw("required_level", Integer.toString(visitor.requiredGardenLevel())),
                TextService.parsed("crop", cropName),
                TextService.raw("amount", text.formatNumber(visitor.requiredCropAmount())),
                TextService.raw("stored", text.formatNumber(stored)),
                TextService.raw("garden_xp", text.formatNumber(visitor.gardenXp())),
                TextService.raw("copper", text.formatNumber(visitor.copper())),
                TextService.raw("farming_xp", text.formatNumber(visitor.farmingXp())),
                TextService.raw("coins", text.formatNumber(visitor.coins())),
                TextService.raw("served", Integer.toString(profile.gardenVisitorServed(visitor.id()))),
                TextService.parsed("status", gardenLevel(profile) >= visitor.requiredGardenLevel() && stored >= visitor.requiredCropAmount() ? "<green>Ready</green>" : "<red>Waiting</red>")
        );
    }

    private int cropMilestone(GardenCropDefinition crop, long harvests) {
        if (crop.milestoneInterval() <= 0L) {
            return 0;
        }
        return Math.min(crop.maxMilestone(), (int) (harvests / crop.milestoneInterval()));
    }

    private long nextCropMilestone(GardenCropDefinition crop, long harvests) {
        int milestone = cropMilestone(crop, harvests);
        if (milestone >= crop.maxMilestone()) {
            return harvests;
        }
        return (long) (milestone + 1) * crop.milestoneInterval();
    }

    private int totalCropMilestones(SkyBlockProfile profile) {
        return cropDefinitions().stream()
                .mapToInt(crop -> cropMilestone(crop, profile.gardenCropHarvests(crop.id())))
                .sum();
    }

    private double nextLevelXp(SkyBlockProfile profile) {
        int level = gardenLevel(profile);
        return gardenLevels.keySet().stream()
                .filter(candidate -> candidate > level)
                .sorted()
                .findFirst()
                .map(gardenLevels::get)
                .orElse(profile.gardenXp());
    }

    private void ensureDefaultPlots(SkyBlockProfile profile) {
        for (GardenPlotDefinition plot : plotDefinitions()) {
            if (plot.unlockedByDefault()) {
                profile.addGardenPlot(plot.id());
            }
        }
    }

    private String rewardDisplay(GardenRewardDefinition reward) {
        if (reward.coinsReward()) {
            return "<gold>" + text.formatNumber(reward.coins()) + " coins</gold>";
        }
        String name = reward.customItem()
                ? customItems.definition(reward.itemId()).map(CustomItemDefinition::displayName).orElse(reward.itemId())
                : readableMaterial(reward.material());
        return "<yellow>" + reward.amount() + "x</yellow> " + name;
    }

    private String readableMaterial(Material material) {
        String normalized = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private String string(Object value, String fallback) {
        return value == null ? fallback : value.toString();
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(string(value, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double decimal(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(string(value, Double.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
