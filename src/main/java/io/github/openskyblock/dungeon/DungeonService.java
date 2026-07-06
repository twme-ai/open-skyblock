package io.github.openskyblock.dungeon;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.service.SkillService;
import io.github.openskyblock.service.SkillType;
import java.time.LocalDate;
import java.time.ZoneOffset;
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

public final class DungeonService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final SkillService skills;
    private final CustomItemService customItems;
    private final Map<String, DungeonClassDefinition> classes = new HashMap<>();
    private final Map<String, DungeonFloorDefinition> floors = new HashMap<>();
    private int maxScore = 300;
    private int dailyBonusRuns = 5;
    private double dailyBonusMultiplier = 1.4D;
    private String defaultClass = "BERSERK";

    public DungeonService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, SkillService skills, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.skills = skills;
        this.customItems = customItems;
    }

    public void reload() {
        maxScore = Math.max(1, configService.dungeons().getInt("settings.max-score", 300));
        dailyBonusRuns = Math.max(0, configService.dungeons().getInt("settings.daily-bonus-runs", 5));
        dailyBonusMultiplier = Math.max(1.0D, configService.dungeons().getDouble("settings.daily-bonus-multiplier", 1.4D));
        defaultClass = configService.dungeons().getString("settings.default-class", "BERSERK").toUpperCase(Locale.ROOT);
        loadClasses();
        loadFloors();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.dungeons", true);
    }

    public List<String> classIds() {
        return classDefinitions().stream().map(DungeonClassDefinition::id).toList();
    }

    public List<String> floorIds() {
        return floorDefinitions().stream().map(DungeonFloorDefinition::id).toList();
    }

    public Optional<DungeonClassDefinition> dungeonClass(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(classes.get(id.toUpperCase(Locale.ROOT)));
    }

    public Optional<DungeonFloorDefinition> floor(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(floors.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<DungeonClassDefinition> classDefinitions() {
        return classes.values().stream()
                .sorted(Comparator.comparing(DungeonClassDefinition::id))
                .toList();
    }

    public List<DungeonFloorDefinition> floorDefinitions() {
        return floors.values().stream()
                .sorted(Comparator.comparingInt(DungeonFloorDefinition::requiredCatacombsLevel).thenComparing(DungeonFloorDefinition::id))
                .toList();
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.dungeon-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        resetDaily(profile);
        String classId = selectedClass(profile);
        DungeonClassDefinition selectedClass = dungeonClass(classId).orElse(null);
        int catacombsLevel = catacombsLevel(profile);
        int classLevel = skills.level(SkillType.DUNGEONEERING, profile.dungeonClassXp(classId));
        text.send(player, "commands.dungeon-status-header", List.of(
                TextService.raw("catacombs_level", Integer.toString(catacombsLevel)),
                TextService.raw("catacombs_xp", text.formatNumber(profile.skillXp(SkillType.DUNGEONEERING))),
                TextService.parsed("class", selectedClass == null ? classId : selectedClass.displayName()),
                TextService.raw("class_level", Integer.toString(classLevel)),
                TextService.raw("daily_runs", Integer.toString(profile.dailyDungeonRuns())),
                TextService.raw("daily_bonus_runs", Integer.toString(dailyBonusRuns))
        ));
        for (DungeonFloorDefinition floor : floorDefinitions()) {
            text.send(player, "commands.dungeon-status-floor", floorPlaceholders(profile, floor));
        }
    }

    public void sendFloors(Player player) {
        if (!enabled()) {
            text.send(player, "commands.dungeon-disabled");
            return;
        }
        if (floors.isEmpty()) {
            text.send(player, "commands.dungeon-empty");
            return;
        }
        text.send(player, "commands.dungeon-floor-list-header");
        for (DungeonFloorDefinition floor : floorDefinitions()) {
            text.send(player, "commands.dungeon-floor-list-line", floorPlaceholders(profiles.profile(player), floor));
        }
    }

    public void sendClasses(Player player) {
        if (!enabled()) {
            text.send(player, "commands.dungeon-disabled");
            return;
        }
        if (classes.isEmpty()) {
            text.send(player, "commands.dungeon-class-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.dungeon-class-list-header");
        for (DungeonClassDefinition definition : classDefinitions()) {
            text.send(player, "commands.dungeon-class-list-line", classPlaceholders(profile, definition));
        }
    }

    public boolean selectClass(Player player, String classId) {
        if (!enabled()) {
            text.send(player, "commands.dungeon-disabled");
            return false;
        }
        DungeonClassDefinition definition = dungeonClass(classId).orElse(null);
        if (definition == null) {
            text.send(player, "commands.dungeon-unknown-class", List.of(TextService.raw("class", classId == null ? "" : classId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        profile.selectedDungeonClass(definition.id());
        profiles.save(player);
        text.send(player, "commands.dungeon-class-selected", classPlaceholders(profile, definition));
        return true;
    }

    public boolean run(Player player, String floorId, int requestedScore) {
        if (!enabled()) {
            text.send(player, "commands.dungeon-disabled");
            return false;
        }
        DungeonFloorDefinition floor = floor(floorId).orElse(null);
        if (floor == null) {
            text.send(player, "commands.dungeon-unknown-floor", List.of(TextService.raw("floor", floorId == null ? "" : floorId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        resetDaily(profile);
        int catacombsLevel = catacombsLevel(profile);
        if (catacombsLevel < floor.requiredCatacombsLevel()) {
            text.send(player, "commands.dungeon-requires-level", floorPlaceholders(profile, floor));
            return false;
        }
        String classId = selectedClass(profile);
        DungeonClassDefinition selectedClass = dungeonClass(classId).orElse(null);
        int score = Math.max(0, Math.min(maxScore, requestedScore));
        DungeonChestDefinition chest = bestChest(floor, score).orElse(null);
        if (chest != null && !economy.spendPurse(player, chest.cost())) {
            text.send(player, "commands.dungeon-no-money", chestPlaceholders(floor, chest, score, List.of()));
            return false;
        }
        double scoreMultiplier = Math.max(0.0D, Math.min(1.0D, score / (double) maxScore));
        double xp = floor.baseXp() * scoreMultiplier;
        boolean dailyBonus = profile.dailyDungeonRuns() < dailyBonusRuns;
        if (dailyBonus) {
            xp *= dailyBonusMultiplier;
        }
        if (xp > 0.0D) {
            skills.addXp(player, SkillType.DUNGEONEERING, xp);
            profile.addDungeonClassXp(classId, xp);
        }
        if (floor.completionCoins() > 0.0D) {
            economy.addPurse(player, floor.completionCoins());
        }
        profile.addDungeonCompletion(floor.id(), 1);
        profile.addDailyDungeonRun();

        List<String> rewards = new ArrayList<>();
        if (chest != null) {
            for (int index = 0; index < Math.max(1, chest.rewardRolls()); index++) {
                selectReward(chest).flatMap(reward -> giveReward(player, reward)).ifPresent(rewards::add);
            }
        }
        profiles.save(player);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(chest == null ? noChestPlaceholders(profile, floor) : chestPlaceholders(floor, chest, score, rewards));
        placeholders.add(TextService.raw("score", Integer.toString(score)));
        placeholders.add(TextService.raw("xp", text.formatNumber(xp)));
        placeholders.add(TextService.raw("completion_coins", text.formatNumber(floor.completionCoins())));
        placeholders.add(TextService.raw("daily_bonus", dailyBonus ? "Yes" : "No"));
        placeholders.add(TextService.parsed("class", selectedClass == null ? classId : selectedClass.displayName()));
        placeholders.add(TextService.parsed("rewards", rewards.isEmpty() ? "<gray>none</gray>" : String.join("<gray>, </gray>", rewards)));
        text.send(player, "commands.dungeon-completed", placeholders);
        return true;
    }

    private void loadClasses() {
        classes.clear();
        ConfigurationSection section = configService.dungeons().getConfigurationSection("classes");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection classSection = section.getConfigurationSection(id);
            if (classSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            classes.put(normalized, new DungeonClassDefinition(
                    normalized,
                    classSection.getString("display-name", normalized),
                    classSection.getString("description", "")
            ));
        }
    }

    private void loadFloors() {
        floors.clear();
        ConfigurationSection section = configService.dungeons().getConfigurationSection("floors");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection floorSection = section.getConfigurationSection(id);
            if (floorSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            List<DungeonChestDefinition> chests = new ArrayList<>();
            ConfigurationSection chestSection = floorSection.getConfigurationSection("chests");
            if (chestSection != null) {
                for (String chestId : chestSection.getKeys(false)) {
                    chest(chestId, chestSection.getConfigurationSection(chestId)).ifPresent(chests::add);
                }
            }
            floors.put(normalized, new DungeonFloorDefinition(
                    normalized,
                    floorSection.getString("display-name", normalized),
                    floorSection.getString("boss", ""),
                    Math.max(0, floorSection.getInt("required-catacombs-level", 0)),
                    Math.max(0.0D, floorSection.getDouble("base-xp", 0.0D)),
                    Math.max(0.0D, floorSection.getDouble("completion-coins", 0.0D)),
                    chests.stream()
                            .sorted(Comparator.comparingInt(DungeonChestDefinition::minScore))
                            .toList()
            ));
        }
    }

    private Optional<DungeonChestDefinition> chest(String id, ConfigurationSection section) {
        if (section == null) {
            return Optional.empty();
        }
        List<DungeonRewardDefinition> rewards = new ArrayList<>();
        for (Map<?, ?> rawReward : section.getMapList("rewards")) {
            reward(rawReward).ifPresent(rewards::add);
        }
        return Optional.of(new DungeonChestDefinition(
                id.toUpperCase(Locale.ROOT),
                section.getString("display-name", id),
                Math.max(0, section.getInt("min-score", 0)),
                Math.max(0.0D, section.getDouble("cost", 0.0D)),
                Math.max(1, section.getInt("reward-rolls", 1)),
                List.copyOf(rewards)
        ));
    }

    private Optional<DungeonRewardDefinition> reward(Map<?, ?> section) {
        String type = string(section.get("type"), "VANILLA").toUpperCase(Locale.ROOT);
        int amount = Math.max(1, integer(section.get("amount"), 1));
        double weight = Math.max(0.0D, decimal(section.get("weight"), 1.0D));
        if (weight <= 0.0D) {
            return Optional.empty();
        }
        if (type.equals("COINS")) {
            double coins = Math.max(0.0D, decimal(section.get("coins"), decimal(section.get("amount"), 0.0D)));
            return coins <= 0.0D ? Optional.empty() : Optional.of(new DungeonRewardDefinition("COINS", "", Material.GOLD_NUGGET, 1, coins, weight));
        }
        if (type.equals("CUSTOM_ITEM")) {
            String itemId = string(section.get("item"), "").toUpperCase(Locale.ROOT);
            return itemId.isBlank() ? Optional.empty() : Optional.of(new DungeonRewardDefinition("CUSTOM_ITEM", itemId, Material.STONE, amount, 0.0D, weight));
        }
        Material material = Material.matchMaterial(string(section.get("material"), "BONE"));
        if (material == null || material.isAir()) {
            return Optional.empty();
        }
        return Optional.of(new DungeonRewardDefinition("VANILLA", "", material, amount, 0.0D, weight));
    }

    private Optional<DungeonChestDefinition> bestChest(DungeonFloorDefinition floor, int score) {
        return floor.chests().stream()
                .filter(chest -> score >= chest.minScore())
                .max(Comparator.comparingInt(DungeonChestDefinition::minScore));
    }

    private Optional<DungeonRewardDefinition> selectReward(DungeonChestDefinition chest) {
        double totalWeight = chest.rewards().stream().mapToDouble(DungeonRewardDefinition::weight).sum();
        if (totalWeight <= 0.0D) {
            return Optional.empty();
        }
        double selected = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0.0D;
        for (DungeonRewardDefinition reward : chest.rewards()) {
            cumulative += reward.weight();
            if (selected <= cumulative) {
                return Optional.of(reward);
            }
        }
        return chest.rewards().stream().filter(reward -> reward.weight() > 0.0D).findFirst();
    }

    private Optional<String> giveReward(Player player, DungeonRewardDefinition reward) {
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

    private List<TextService.TextPlaceholder> floorPlaceholders(SkyBlockProfile profile, DungeonFloorDefinition floor) {
        return List.of(
                TextService.raw("id", floor.id()),
                TextService.parsed("floor", floor.displayName()),
                TextService.raw("boss", floor.bossName()),
                TextService.raw("required_level", Integer.toString(floor.requiredCatacombsLevel())),
                TextService.raw("base_xp", text.formatNumber(floor.baseXp())),
                TextService.raw("completion_coins", text.formatNumber(floor.completionCoins())),
                TextService.raw("completions", Integer.toString(profile.dungeonCompletions(floor.id()))),
                TextService.parsed("chests", chestList(floor))
        );
    }

    private List<TextService.TextPlaceholder> classPlaceholders(SkyBlockProfile profile, DungeonClassDefinition definition) {
        boolean selected = definition.id().equals(selectedClass(profile));
        return List.of(
                TextService.raw("id", definition.id()),
                TextService.parsed("class", definition.displayName()),
                TextService.raw("description", definition.description()),
                TextService.raw("class_xp", text.formatNumber(profile.dungeonClassXp(definition.id()))),
                TextService.raw("class_level", Integer.toString(skills.level(SkillType.DUNGEONEERING, profile.dungeonClassXp(definition.id())))),
                TextService.parsed("status", selected ? "<green>Selected</green>" : "<gray>Available</gray>")
        );
    }

    private List<TextService.TextPlaceholder> chestPlaceholders(DungeonFloorDefinition floor, DungeonChestDefinition chest, int score, List<String> rewards) {
        return List.of(
                TextService.raw("id", floor.id()),
                TextService.parsed("floor", floor.displayName()),
                TextService.raw("boss", floor.bossName()),
                TextService.raw("score", Integer.toString(score)),
                TextService.raw("chest_id", chest.id()),
                TextService.parsed("chest", chest.displayName()),
                TextService.raw("min_score", Integer.toString(chest.minScore())),
                TextService.raw("cost", text.formatNumber(chest.cost())),
                TextService.parsed("rewards", rewards.isEmpty() ? "<gray>none</gray>" : String.join("<gray>, </gray>", rewards))
        );
    }

    private List<TextService.TextPlaceholder> noChestPlaceholders(SkyBlockProfile profile, DungeonFloorDefinition floor) {
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(floorPlaceholders(profile, floor));
        placeholders.add(TextService.raw("chest_id", "NONE"));
        placeholders.add(TextService.parsed("chest", "<gray>No Chest</gray>"));
        placeholders.add(TextService.raw("min_score", "0"));
        placeholders.add(TextService.raw("cost", "0"));
        placeholders.add(TextService.parsed("rewards", "<gray>none</gray>"));
        return placeholders;
    }

    private String chestList(DungeonFloorDefinition floor) {
        if (floor.chests().isEmpty()) {
            return "<gray>none</gray>";
        }
        return String.join("<gray>, </gray>", floor.chests().stream()
                .map(chest -> chest.displayName() + " <dark_gray>(" + chest.minScore() + "+)</dark_gray>")
                .toList());
    }

    private String selectedClass(SkyBlockProfile profile) {
        String selected = profile.selectedDungeonClass();
        if (selected == null || selected.isBlank() || !classes.containsKey(selected.toUpperCase(Locale.ROOT))) {
            return classes.containsKey(defaultClass) ? defaultClass : classes.keySet().stream().sorted().findFirst().orElse(defaultClass);
        }
        return selected.toUpperCase(Locale.ROOT);
    }

    private int catacombsLevel(SkyBlockProfile profile) {
        return skills.level(SkillType.DUNGEONEERING, profile.skillXp(SkillType.DUNGEONEERING));
    }

    private void resetDaily(SkyBlockProfile profile) {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        if (!today.equals(profile.dungeonRunDay())) {
            profile.dungeonRunDay(today);
            profile.dailyDungeonRuns(0);
        }
    }

    private String rewardDisplay(DungeonRewardDefinition reward) {
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
