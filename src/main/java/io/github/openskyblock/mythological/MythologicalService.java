package io.github.openskyblock.mythological;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.mayor.MayorService;
import io.github.openskyblock.pet.PetService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.service.Rarity;
import io.github.openskyblock.service.SkillService;
import io.github.openskyblock.service.SkillType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
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

public final class MythologicalService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final SkillService skills;
    private final CustomItemService customItems;
    private final MayorService mayors;
    private final PetService pets;
    private final Map<String, MythologicalMobDefinition> mobs = new HashMap<>();
    private final Map<String, MythologicalTreasureDefinition> treasures = new HashMap<>();
    private final Map<Rarity, Double> griffinUpgradeCosts = new EnumMap<>(Rarity.class);
    private int burrowsPerChain = 4;
    private boolean requireDiana = true;
    private String griffinPetId = "GRIFFIN";
    private double griffinCost = 25000.0D;
    private String featherItemId = "GRIFFIN_FEATHER";
    private double mobChance = 0.55D;
    private double skyBlockXpPerBurrow = 0.02D;
    private double skyBlockXpPerUniqueMob = 1.0D;
    private double skyBlockXpPerUniqueTreasure = 0.75D;

    public MythologicalService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, SkillService skills, CustomItemService customItems, MayorService mayors, PetService pets) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.skills = skills;
        this.customItems = customItems;
        this.mayors = mayors;
        this.pets = pets;
    }

    public void reload() {
        burrowsPerChain = Math.max(1, configService.mythological().getInt("settings.burrows-per-chain", 4));
        requireDiana = configService.mythological().getBoolean("settings.require-diana", true);
        griffinPetId = configService.mythological().getString("settings.griffin-pet", "GRIFFIN").toUpperCase(Locale.ROOT);
        griffinCost = Math.max(0.0D, configService.mythological().getDouble("settings.griffin-cost", 25000.0D));
        featherItemId = configService.mythological().getString("settings.feather-item", "GRIFFIN_FEATHER").toUpperCase(Locale.ROOT);
        mobChance = Math.max(0.0D, Math.min(1.0D, configService.mythological().getDouble("settings.mob-chance", 0.55D)));
        skyBlockXpPerBurrow = Math.max(0.0D, configService.mythological().getDouble("settings.skyblock-xp-per-burrow", 0.02D));
        skyBlockXpPerUniqueMob = Math.max(0.0D, configService.mythological().getDouble("settings.skyblock-xp-per-unique-mob", 1.0D));
        skyBlockXpPerUniqueTreasure = Math.max(0.0D, configService.mythological().getDouble("settings.skyblock-xp-per-unique-treasure", 0.75D));
        loadUpgradeCosts();
        loadMobs();
        loadTreasures();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.mythological-ritual", true);
    }

    public boolean ritualActive() {
        return !requireDiana || mayors.modifier("mythological_event_enabled") > 0.0D;
    }

    public List<String> mobIds() {
        return mobDefinitions().stream().map(MythologicalMobDefinition::id).toList();
    }

    public List<String> treasureIds() {
        return treasureDefinitions().stream().map(MythologicalTreasureDefinition::id).toList();
    }

    public List<MythologicalMobDefinition> mobDefinitions() {
        return mobs.values().stream()
                .sorted(Comparator.comparing(MythologicalMobDefinition::minimumRarity).thenComparing(MythologicalMobDefinition::id))
                .toList();
    }

    public List<MythologicalTreasureDefinition> treasureDefinitions() {
        return treasures.values().stream()
                .sorted(Comparator.comparing(MythologicalTreasureDefinition::minimumRarity).thenComparing(MythologicalTreasureDefinition::id))
                .toList();
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mythological-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.mythological-status", List.of(
                TextService.parsed("active", ritualActive() ? "<green>Active</green>" : "<red>Inactive</red>"),
                TextService.raw("griffin", griffinRarity(profile).map(Enum::name).orElse("NONE")),
                TextService.raw("chain", Integer.toString(profile.mythologicalBurrowChain())),
                TextService.raw("chain_required", Integer.toString(burrowsPerChain)),
                TextService.raw("burrows", text.formatNumber(profile.mythologicalBurrowsDug())),
                TextService.raw("mobs", text.formatNumber(total(profile.mythologicalMobKills()))),
                TextService.raw("treasures", text.formatNumber(total(profile.mythologicalTreasures()))),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile)))
        ));
    }

    public void sendMobs(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mythological-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.mythological-mob-header");
        for (MythologicalMobDefinition mob : mobDefinitions()) {
            text.send(player, "commands.mythological-mob-line", mobPlaceholders(profile, mob));
        }
    }

    public void sendTreasures(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mythological-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.mythological-treasure-header");
        for (MythologicalTreasureDefinition treasure : treasureDefinitions()) {
            text.send(player, "commands.mythological-treasure-line", treasurePlaceholders(profile, treasure));
        }
    }

    public void sendShop(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mythological-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        Rarity rarity = griffinRarity(profile).orElse(null);
        text.send(player, "commands.mythological-shop", List.of(
                TextService.raw("griffin", rarity == null ? "NONE" : rarity.name()),
                TextService.raw("griffin_cost", text.formatNumber(griffinCost)),
                TextService.raw("next_rarity", nextRarity(rarity).map(Enum::name).orElse("MAX")),
                TextService.raw("upgrade_cost", nextRarity(rarity).map(next -> text.formatNumber(griffinUpgradeCosts.getOrDefault(next, 0.0D))).orElse("0")),
                TextService.raw("feathers", Integer.toString(countItem(player, featherItemId)))
        ));
    }

    public boolean buyGriffin(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mythological-disabled");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (griffinRarity(profile).isPresent()) {
            text.send(player, "commands.mythological-griffin-owned", List.of(TextService.raw("griffin", profile.mythologicalGriffinRarity())));
            return false;
        }
        if (griffinCost > 0.0D && !economy.spendPurse(player, griffinCost)) {
            text.send(player, "commands.mythological-no-money", List.of(TextService.raw("cost", text.formatNumber(griffinCost))));
            return false;
        }
        profile.mythologicalGriffinRarity(Rarity.COMMON.name());
        pets.definition(griffinPetId).ifPresent(definition -> pets.addPet(profile, definition));
        profiles.save(player);
        text.send(player, "commands.mythological-griffin-bought", List.of(
                TextService.raw("griffin", Rarity.COMMON.name()),
                TextService.raw("cost", text.formatNumber(griffinCost))
        ));
        return true;
    }

    public boolean upgradeGriffin(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mythological-disabled");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        Rarity current = griffinRarity(profile).orElse(null);
        if (current == null) {
            text.send(player, "commands.mythological-no-griffin");
            return false;
        }
        Rarity next = nextRarity(current).orElse(null);
        if (next == null) {
            text.send(player, "commands.mythological-griffin-maxed", List.of(TextService.raw("griffin", current.name())));
            return false;
        }
        double cost = griffinUpgradeCosts.getOrDefault(next, 0.0D);
        if (cost > 0.0D && !economy.spendPurse(player, cost)) {
            text.send(player, "commands.mythological-no-money", List.of(TextService.raw("cost", text.formatNumber(cost))));
            return false;
        }
        profile.mythologicalGriffinRarity(next.name());
        profiles.save(player);
        text.send(player, "commands.mythological-griffin-upgraded", List.of(
                TextService.raw("griffin", next.name()),
                TextService.raw("cost", text.formatNumber(cost))
        ));
        return true;
    }

    public boolean dig(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mythological-disabled");
            return false;
        }
        if (!ritualActive()) {
            text.send(player, "commands.mythological-inactive");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        Rarity griffin = griffinRarity(profile).orElse(null);
        if (griffin == null) {
            text.send(player, "commands.mythological-no-griffin");
            return false;
        }
        profile.addMythologicalBurrow();
        int chain = Math.min(burrowsPerChain, profile.mythologicalBurrowChain() + 1);
        profile.mythologicalBurrowChain(chain);
        if (chain < burrowsPerChain) {
            profiles.save(player);
            text.send(player, "commands.mythological-burrow-progress", List.of(
                    TextService.raw("chain", Integer.toString(chain)),
                    TextService.raw("chain_required", Integer.toString(burrowsPerChain)),
                    TextService.raw("burrows", text.formatNumber(profile.mythologicalBurrowsDug()))
            ));
            return true;
        }
        profile.mythologicalBurrowChain(0);
        if (ThreadLocalRandom.current().nextDouble() < mobChance) {
            return digMob(player, profile, griffin);
        }
        return digTreasure(player, profile, griffin);
    }

    public boolean resetChain(Player player) {
        if (!enabled()) {
            text.send(player, "commands.mythological-disabled");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        profile.mythologicalBurrowChain(0);
        profiles.save(player);
        text.send(player, "commands.mythological-chain-reset");
        return true;
    }

    public double skyBlockXp(SkyBlockProfile profile) {
        double xp = profile.mythologicalBurrowsDug() * skyBlockXpPerBurrow;
        xp += profile.mythologicalMobKills().size() * skyBlockXpPerUniqueMob;
        xp += profile.mythologicalTreasures().size() * skyBlockXpPerUniqueTreasure;
        return Math.max(0.0D, xp);
    }

    private boolean digMob(Player player, SkyBlockProfile profile, Rarity griffin) {
        MythologicalMobDefinition mob = selectMob(griffin).orElse(null);
        if (mob == null) {
            text.send(player, "commands.mythological-no-encounter");
            profiles.save(player);
            return false;
        }
        List<String> rewards = new ArrayList<>();
        if (mob.combatXp() > 0.0D) {
            skills.addXp(player, SkillType.COMBAT, mob.combatXp());
        }
        if (mob.coins() > 0.0D) {
            economy.addPurse(player, mob.coins());
        }
        selectRewards(mob.rewards(), griffin).stream()
                .map(reward -> giveReward(player, reward))
                .flatMap(Optional::stream)
                .forEach(rewards::add);
        profile.addMythologicalMobKill(mob.id(), 1);
        profiles.save(player);
        text.send(player, "commands.mythological-mob-found", List.of(
                TextService.parsed("mob", mob.displayName()),
                TextService.raw("griffin", griffin.name()),
                TextService.raw("combat_xp", text.formatNumber(mob.combatXp())),
                TextService.raw("coins", text.formatNumber(mob.coins())),
                TextService.raw("kills", Integer.toString(profile.mythologicalMobKills(mob.id()))),
                TextService.parsed("rewards", rewards.isEmpty() ? "<gray>none</gray>" : String.join("<gray>, </gray>", rewards))
        ));
        return true;
    }

    private boolean digTreasure(Player player, SkyBlockProfile profile, Rarity griffin) {
        MythologicalTreasureDefinition treasure = selectTreasure(griffin).orElse(null);
        if (treasure == null) {
            text.send(player, "commands.mythological-no-encounter");
            profiles.save(player);
            return false;
        }
        List<String> rewards = new ArrayList<>();
        if (treasure.coins() > 0.0D) {
            economy.addPurse(player, treasure.coins());
        }
        selectRewards(treasure.rewards(), griffin).stream()
                .map(reward -> giveReward(player, reward))
                .flatMap(Optional::stream)
                .forEach(rewards::add);
        profile.addMythologicalTreasure(treasure.id(), 1);
        profiles.save(player);
        text.send(player, "commands.mythological-treasure-found", List.of(
                TextService.parsed("treasure", treasure.displayName()),
                TextService.raw("griffin", griffin.name()),
                TextService.raw("coins", text.formatNumber(treasure.coins())),
                TextService.raw("found", Integer.toString(profile.mythologicalTreasures(treasure.id()))),
                TextService.parsed("rewards", rewards.isEmpty() ? "<gray>none</gray>" : String.join("<gray>, </gray>", rewards))
        ));
        return true;
    }

    private Optional<MythologicalMobDefinition> selectMob(Rarity griffin) {
        return selectWeighted(mobDefinitions().stream()
                .filter(mob -> rarityAtLeast(griffin, mob.minimumRarity()))
                .toList(), MythologicalMobDefinition::weight);
    }

    private Optional<MythologicalTreasureDefinition> selectTreasure(Rarity griffin) {
        return selectWeighted(treasureDefinitions().stream()
                .filter(treasure -> rarityAtLeast(griffin, treasure.minimumRarity()))
                .toList(), MythologicalTreasureDefinition::weight);
    }

    private List<MythologicalRewardDefinition> selectRewards(List<MythologicalRewardDefinition> rewards, Rarity griffin) {
        List<MythologicalRewardDefinition> eligible = rewards.stream()
                .filter(reward -> rarityAtLeast(griffin, reward.minimumRarity()))
                .toList();
        return selectWeighted(eligible, MythologicalRewardDefinition::weight)
                .map(List::of)
                .orElseGet(List::of);
    }

    private <T> Optional<T> selectWeighted(List<T> values, java.util.function.ToDoubleFunction<T> weightFunction) {
        double total = values.stream().mapToDouble(weightFunction).filter(weight -> weight > 0.0D).sum();
        if (total <= 0.0D) {
            return Optional.empty();
        }
        double selected = ThreadLocalRandom.current().nextDouble(total);
        double cursor = 0.0D;
        for (T value : values) {
            double weight = Math.max(0.0D, weightFunction.applyAsDouble(value));
            cursor += weight;
            if (selected <= cursor) {
                return Optional.of(value);
            }
        }
        return values.stream().findFirst();
    }

    private Optional<String> giveReward(Player player, MythologicalRewardDefinition reward) {
        if (reward.coinsReward()) {
            economy.addPurse(player, reward.coins());
            return Optional.of("<gold>" + text.formatNumber(reward.coins()) + " coins</gold>");
        }
        if (reward.customItem()) {
            CustomItemDefinition definition = customItems.definition(reward.itemId()).orElse(null);
            if (definition == null) {
                return Optional.empty();
            }
            giveCustomItem(player, definition, reward.amount());
            return Optional.of("<yellow>" + reward.amount() + "x</yellow> " + definition.displayName());
        }
        ItemStack itemStack = new ItemStack(reward.material());
        itemStack.setAmount(reward.amount());
        giveItem(player, itemStack);
        return Optional.of("<yellow>" + reward.amount() + "x</yellow> " + readableMaterial(reward.material()));
    }

    private void giveCustomItem(Player player, CustomItemDefinition definition, int amount) {
        int remaining = Math.max(1, amount);
        int maxStack = Math.max(1, definition.material().getMaxStackSize());
        while (remaining > 0) {
            int stackAmount = Math.min(maxStack, remaining);
            ItemStack itemStack = customItems.createItem(definition);
            itemStack.setAmount(stackAmount);
            giveItem(player, itemStack);
            remaining -= stackAmount;
        }
    }

    private void giveItem(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private int countItem(Player player, String itemId) {
        int amount = 0;
        for (ItemStack itemStack : player.getInventory().getStorageContents()) {
            if (matches(itemStack, itemId)) {
                amount += itemStack.getAmount();
            }
        }
        return amount;
    }

    private boolean matches(ItemStack itemStack, String itemId) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        return customItems.definition(itemStack)
                .map(CustomItemDefinition::id)
                .map(id -> id.equalsIgnoreCase(itemId))
                .orElse(false);
    }

    private Optional<Rarity> griffinRarity(SkyBlockProfile profile) {
        String raw = profile.mythologicalGriffinRarity();
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Rarity.parse(raw));
    }

    private Optional<Rarity> nextRarity(Rarity rarity) {
        if (rarity == null) {
            return Optional.of(Rarity.COMMON);
        }
        return switch (rarity) {
            case COMMON -> Optional.of(Rarity.UNCOMMON);
            case UNCOMMON -> Optional.of(Rarity.RARE);
            case RARE -> Optional.of(Rarity.EPIC);
            case EPIC -> Optional.of(Rarity.LEGENDARY);
            case LEGENDARY, MYTHIC, DIVINE, SPECIAL, VERY_SPECIAL -> Optional.empty();
        };
    }

    private boolean rarityAtLeast(Rarity actual, Rarity required) {
        return rarityRank(actual) >= rarityRank(required);
    }

    private int rarityRank(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> 0;
            case UNCOMMON -> 1;
            case RARE -> 2;
            case EPIC -> 3;
            case LEGENDARY -> 4;
            case MYTHIC -> 5;
            case DIVINE -> 6;
            case SPECIAL -> 7;
            case VERY_SPECIAL -> 8;
        };
    }

    private void loadUpgradeCosts() {
        griffinUpgradeCosts.clear();
        ConfigurationSection section = configService.mythological().getConfigurationSection("griffin-upgrades");
        if (section == null) {
            return;
        }
        for (String rarityKey : section.getKeys(false)) {
            Rarity rarity = Rarity.parse(rarityKey);
            griffinUpgradeCosts.put(rarity, Math.max(0.0D, section.getDouble(rarityKey, 0.0D)));
        }
    }

    private void loadMobs() {
        mobs.clear();
        ConfigurationSection section = configService.mythological().getConfigurationSection("mobs");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection mob = section.getConfigurationSection(id);
            if (mob == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            mobs.put(normalized, new MythologicalMobDefinition(
                    normalized,
                    mob.getString("display-name", normalized),
                    Rarity.parse(mob.getString("minimum-griffin-rarity", "COMMON")),
                    Math.max(0.0D, mob.getDouble("weight", 1.0D)),
                    Math.max(0.0D, mob.getDouble("combat-xp", 0.0D)),
                    Math.max(0.0D, mob.getDouble("coins", 0.0D)),
                    readRewards(mob.getMapList("rewards"))
            ));
        }
    }

    private void loadTreasures() {
        treasures.clear();
        ConfigurationSection section = configService.mythological().getConfigurationSection("treasures");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection treasure = section.getConfigurationSection(id);
            if (treasure == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            treasures.put(normalized, new MythologicalTreasureDefinition(
                    normalized,
                    treasure.getString("display-name", normalized),
                    Rarity.parse(treasure.getString("minimum-griffin-rarity", "COMMON")),
                    Math.max(0.0D, treasure.getDouble("weight", 1.0D)),
                    Math.max(0.0D, treasure.getDouble("coins", 0.0D)),
                    readRewards(treasure.getMapList("rewards"))
            ));
        }
    }

    private List<MythologicalRewardDefinition> readRewards(List<Map<?, ?>> rewardMaps) {
        List<MythologicalRewardDefinition> rewards = new ArrayList<>();
        for (Map<?, ?> rawReward : rewardMaps) {
            reward(rawReward).ifPresent(rewards::add);
        }
        return List.copyOf(rewards);
    }

    private Optional<MythologicalRewardDefinition> reward(Map<?, ?> section) {
        String type = string(section.get("type"), "VANILLA").toUpperCase(Locale.ROOT);
        int amount = Math.max(1, integer(section.get("amount"), 1));
        double weight = Math.max(0.0D, decimal(section.get("weight"), 1.0D));
        Rarity minimumRarity = Rarity.parse(string(section.get("minimum-griffin-rarity"), "COMMON"));
        if (weight <= 0.0D) {
            return Optional.empty();
        }
        if (type.equals("COINS")) {
            double coins = Math.max(0.0D, decimal(section.get("coins"), decimal(section.get("amount"), 0.0D)));
            return coins <= 0.0D ? Optional.empty() : Optional.of(new MythologicalRewardDefinition("COINS", "", Material.GOLD_NUGGET, 1, coins, weight, minimumRarity));
        }
        if (type.equals("CUSTOM_ITEM")) {
            String itemId = string(section.get("item"), "").toUpperCase(Locale.ROOT);
            return itemId.isBlank() ? Optional.empty() : Optional.of(new MythologicalRewardDefinition("CUSTOM_ITEM", itemId, Material.STONE, amount, 0.0D, weight, minimumRarity));
        }
        Material material = Material.matchMaterial(string(section.get("material"), "DIRT"));
        if (material == null || material.isAir()) {
            return Optional.empty();
        }
        return Optional.of(new MythologicalRewardDefinition("VANILLA", "", material, amount, 0.0D, weight, minimumRarity));
    }

    private List<TextService.TextPlaceholder> mobPlaceholders(SkyBlockProfile profile, MythologicalMobDefinition mob) {
        return List.of(
                TextService.raw("id", mob.id()),
                TextService.parsed("mob", mob.displayName()),
                TextService.raw("minimum_griffin", mob.minimumRarity().name()),
                TextService.raw("combat_xp", text.formatNumber(mob.combatXp())),
                TextService.raw("coins", text.formatNumber(mob.coins())),
                TextService.raw("kills", Integer.toString(profile.mythologicalMobKills(mob.id())))
        );
    }

    private List<TextService.TextPlaceholder> treasurePlaceholders(SkyBlockProfile profile, MythologicalTreasureDefinition treasure) {
        return List.of(
                TextService.raw("id", treasure.id()),
                TextService.parsed("treasure", treasure.displayName()),
                TextService.raw("minimum_griffin", treasure.minimumRarity().name()),
                TextService.raw("coins", text.formatNumber(treasure.coins())),
                TextService.raw("found", Integer.toString(profile.mythologicalTreasures(treasure.id())))
        );
    }

    private long total(Map<String, Integer> values) {
        return values.values().stream().mapToLong(Integer::longValue).sum();
    }

    private String readableMaterial(Material material) {
        String normalized = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return normalized.isBlank() ? material.name() : Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
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
