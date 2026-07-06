package io.github.openskyblock.dragon;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
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

public final class DragonService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final SkillService skills;
    private final CustomItemService customItems;
    private final Map<String, DragonDefinition> dragons = new HashMap<>();
    private int eyesRequired = 8;
    private int maxPlacedEyes = 8;
    private String summoningEyeItemId = "SUMMONING_EYE";
    private double baseCoinReward = 5000.0D;
    private double damageForRankOne = 1_000_000.0D;
    private double damageForRankTwo = 500_000.0D;
    private double damageForRankThree = 100_000.0D;
    private double skyBlockXpPerKill = 0.25D;
    private double skyBlockXpPerUniqueDragon = 2.0D;

    public DragonService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, SkillService skills, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.skills = skills;
        this.customItems = customItems;
    }

    public void reload() {
        eyesRequired = Math.max(1, configService.dragons().getInt("settings.eyes-required", 8));
        maxPlacedEyes = Math.max(eyesRequired, configService.dragons().getInt("settings.max-placed-eyes", 8));
        summoningEyeItemId = configService.dragons().getString("settings.summoning-eye-item", "SUMMONING_EYE").toUpperCase(Locale.ROOT);
        baseCoinReward = Math.max(0.0D, configService.dragons().getDouble("settings.base-coin-reward", 5000.0D));
        damageForRankOne = Math.max(1.0D, configService.dragons().getDouble("settings.damage-ranks.first", 1_000_000.0D));
        damageForRankTwo = Math.max(1.0D, configService.dragons().getDouble("settings.damage-ranks.second", 500_000.0D));
        damageForRankThree = Math.max(1.0D, configService.dragons().getDouble("settings.damage-ranks.third", 100_000.0D));
        skyBlockXpPerKill = Math.max(0.0D, configService.dragons().getDouble("settings.skyblock-xp-per-kill", 0.25D));
        skyBlockXpPerUniqueDragon = Math.max(0.0D, configService.dragons().getDouble("settings.skyblock-xp-per-unique-dragon", 2.0D));
        loadDragons();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.dragons", true);
    }

    public List<String> dragonIds() {
        return definitions().stream().map(DragonDefinition::id).toList();
    }

    public Optional<DragonDefinition> dragon(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(dragons.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<DragonDefinition> definitions() {
        return dragons.values().stream()
                .sorted(Comparator.comparing(DragonDefinition::id))
                .toList();
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.dragon-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.dragon-status", List.of(
                TextService.raw("eyes", Integer.toString(profile.placedDragonEyes())),
                TextService.raw("eyes_required", Integer.toString(eyesRequired)),
                TextService.raw("kills", text.formatNumber(profile.totalDragonKills())),
                TextService.raw("eyes_used", text.formatNumber(profile.summoningEyesUsed())),
                TextService.raw("best_damage", text.formatNumber(profile.bestDragonDamage())),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile)))
        ));
        for (DragonDefinition definition : definitions()) {
            text.send(player, "commands.dragon-status-line", dragonPlaceholders(profile, definition));
        }
    }

    public void sendList(Player player) {
        if (!enabled()) {
            text.send(player, "commands.dragon-disabled");
            return;
        }
        if (dragons.isEmpty()) {
            text.send(player, "commands.dragon-empty");
            return;
        }
        text.send(player, "commands.dragon-list-header");
        for (DragonDefinition definition : definitions()) {
            text.send(player, "commands.dragon-list-line", dragonPlaceholders(profiles.profile(player), definition));
        }
    }

    public boolean placeEyes(Player player, int amount) {
        if (!enabled()) {
            text.send(player, "commands.dragon-disabled");
            return false;
        }
        if (amount <= 0) {
            text.send(player, "errors.invalid-number");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        int space = Math.max(0, maxPlacedEyes - profile.placedDragonEyes());
        int placing = Math.min(amount, space);
        if (placing <= 0) {
            text.send(player, "commands.dragon-altar-full", altarPlaceholders(profile));
            return false;
        }
        if (countItem(player, summoningEyeItemId) < placing) {
            text.send(player, "commands.dragon-no-eyes", List.of(TextService.raw("amount", Integer.toString(placing))));
            return false;
        }
        consumeItem(player, summoningEyeItemId, placing);
        profile.placedDragonEyes(profile.placedDragonEyes() + placing);
        profile.addSummoningEyesUsed(placing);
        profiles.save(player);
        text.send(player, "commands.dragon-eyes-placed", altarPlaceholders(profile));
        if (profile.placedDragonEyes() >= eyesRequired) {
            text.send(player, "commands.dragon-ready", altarPlaceholders(profile));
        }
        return true;
    }

    public boolean fight(Player player, double damage, String requestedDragonId) {
        if (!enabled()) {
            text.send(player, "commands.dragon-disabled");
            return false;
        }
        if (damage <= 0.0D || Double.isNaN(damage) || Double.isInfinite(damage)) {
            text.send(player, "errors.invalid-number");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.placedDragonEyes() < eyesRequired) {
            text.send(player, "commands.dragon-not-ready", altarPlaceholders(profile));
            return false;
        }
        DragonDefinition dragon = requestedDragonId == null || requestedDragonId.isBlank()
                ? selectDragon().orElse(null)
                : dragon(requestedDragonId).orElse(null);
        if (dragon == null) {
            text.send(player, "commands.dragon-unknown", List.of(TextService.raw("dragon", requestedDragonId == null ? "" : requestedDragonId)));
            return false;
        }
        int eyes = profile.placedDragonEyes();
        int rank = damageRank(damage);
        List<String> rewards = new ArrayList<>();
        giveFragments(player, dragon, eyes, rank).ifPresent(rewards::add);
        selectReward(dragon, eyes, damage).flatMap(reward -> giveReward(player, reward)).ifPresent(rewards::add);
        double coins = baseCoinReward * Math.max(1, 5 - rank) + eyes * 500.0D;
        if (coins > 0.0D) {
            economy.addPurse(player, coins);
        }
        if (dragon.combatXp() > 0.0D) {
            skills.addXp(player, SkillType.COMBAT, dragon.combatXp());
        }
        profile.addDragonKill(dragon.id(), 1);
        profile.recordDragonDamage(damage);
        profile.placedDragonEyes(0);
        profiles.save(player);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(dragonPlaceholders(profile, dragon));
        placeholders.add(TextService.raw("damage", text.formatNumber(damage)));
        placeholders.add(TextService.raw("rank", Integer.toString(rank)));
        placeholders.add(TextService.raw("eyes", Integer.toString(eyes)));
        placeholders.add(TextService.raw("coins", text.formatNumber(coins)));
        placeholders.add(TextService.parsed("rewards", rewards.isEmpty() ? "<gray>none</gray>" : String.join("<gray>, </gray>", rewards)));
        text.send(player, "commands.dragon-defeated", placeholders);
        return true;
    }

    public double skyBlockXp(SkyBlockProfile profile) {
        return profile.totalDragonKills() * skyBlockXpPerKill + profile.dragonKills().keySet().size() * skyBlockXpPerUniqueDragon;
    }

    private void loadDragons() {
        dragons.clear();
        ConfigurationSection section = configService.dragons().getConfigurationSection("dragons");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection dragonSection = section.getConfigurationSection(id);
            if (dragonSection == null) {
                continue;
            }
            List<DragonRewardDefinition> rewards = new ArrayList<>();
            for (Map<?, ?> rawReward : dragonSection.getMapList("rewards")) {
                reward(rawReward).ifPresent(rewards::add);
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            dragons.put(normalized, new DragonDefinition(
                    normalized,
                    dragonSection.getString("display-name", normalized),
                    Math.max(0.0D, dragonSection.getDouble("spawn-weight", 1.0D)),
                    Math.max(1.0D, dragonSection.getDouble("health", 10_000_000.0D)),
                    Math.max(0.0D, dragonSection.getDouble("combat-xp", 500.0D)),
                    dragonSection.getString("fragment-item", "").toUpperCase(Locale.ROOT),
                    Math.max(0, dragonSection.getInt("base-fragments", 3)),
                    Math.max(0, dragonSection.getInt("fragments-per-eye", 2)),
                    Math.max(0, dragonSection.getInt("fragments-per-rank", 2)),
                    List.copyOf(rewards)
            ));
        }
    }

    private Optional<DragonRewardDefinition> reward(Map<?, ?> section) {
        String type = string(section.get("type"), "VANILLA").toUpperCase(Locale.ROOT);
        int amount = Math.max(1, integer(section.get("amount"), 1));
        double weight = Math.max(0.0D, decimal(section.get("weight"), 1.0D));
        int minEyes = Math.max(0, integer(section.get("min-eyes"), 0));
        double minDamage = Math.max(0.0D, decimal(section.get("min-damage"), 0.0D));
        if (weight <= 0.0D) {
            return Optional.empty();
        }
        if (type.equals("COINS")) {
            double coins = Math.max(0.0D, decimal(section.get("coins"), decimal(section.get("amount"), 0.0D)));
            return coins <= 0.0D ? Optional.empty() : Optional.of(new DragonRewardDefinition("COINS", "", Material.GOLD_NUGGET, 1, coins, weight, minEyes, minDamage));
        }
        if (type.equals("CUSTOM_ITEM")) {
            String itemId = string(section.get("item"), "").toUpperCase(Locale.ROOT);
            return itemId.isBlank() ? Optional.empty() : Optional.of(new DragonRewardDefinition("CUSTOM_ITEM", itemId, Material.STONE, amount, 0.0D, weight, minEyes, minDamage));
        }
        Material material = Material.matchMaterial(string(section.get("material"), "ENDER_PEARL"));
        if (material == null || material.isAir()) {
            return Optional.empty();
        }
        return Optional.of(new DragonRewardDefinition("VANILLA", "", material, amount, 0.0D, weight, minEyes, minDamage));
    }

    private Optional<DragonDefinition> selectDragon() {
        double totalWeight = dragons.values().stream().mapToDouble(DragonDefinition::spawnWeight).sum();
        if (totalWeight <= 0.0D) {
            return Optional.empty();
        }
        double selected = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0.0D;
        for (DragonDefinition dragon : definitions()) {
            cumulative += dragon.spawnWeight();
            if (selected <= cumulative) {
                return Optional.of(dragon);
            }
        }
        return definitions().stream().findFirst();
    }

    private Optional<DragonRewardDefinition> selectReward(DragonDefinition dragon, int eyes, double damage) {
        List<DragonRewardDefinition> eligible = dragon.rewards().stream()
                .filter(reward -> eyes >= reward.minEyes() && damage >= reward.minDamage())
                .toList();
        double totalWeight = eligible.stream().mapToDouble(DragonRewardDefinition::weight).sum();
        if (totalWeight <= 0.0D) {
            return Optional.empty();
        }
        double selected = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0.0D;
        for (DragonRewardDefinition reward : eligible) {
            cumulative += reward.weight();
            if (selected <= cumulative) {
                return Optional.of(reward);
            }
        }
        return eligible.stream().findFirst();
    }

    private Optional<String> giveFragments(Player player, DragonDefinition dragon, int eyes, int rank) {
        if (dragon.fragmentItemId().isBlank()) {
            return Optional.empty();
        }
        CustomItemDefinition definition = customItems.definition(dragon.fragmentItemId()).orElse(null);
        if (definition == null) {
            return Optional.empty();
        }
        int amount = dragon.baseFragments() + eyes * dragon.fragmentsPerEye() + Math.max(0, 4 - rank) * dragon.fragmentsPerRank();
        if (amount <= 0) {
            return Optional.empty();
        }
        ItemStack itemStack = customItems.createItem(definition);
        itemStack.setAmount(amount);
        giveItem(player, itemStack);
        return Optional.of("<yellow>" + amount + "x</yellow> " + definition.displayName());
    }

    private Optional<String> giveReward(Player player, DragonRewardDefinition reward) {
        if (reward.coinsReward()) {
            economy.addPurse(player, reward.coins());
            return Optional.of("<gold>" + text.formatNumber(reward.coins()) + " coins</gold>");
        }
        ItemStack itemStack;
        String display;
        if (reward.customItem()) {
            CustomItemDefinition definition = customItems.definition(reward.itemId()).orElse(null);
            if (definition == null) {
                return Optional.empty();
            }
            itemStack = customItems.createItem(definition);
            display = definition.displayName();
        } else {
            itemStack = new ItemStack(reward.material());
            display = readableMaterial(reward.material());
        }
        itemStack.setAmount(reward.amount());
        giveItem(player, itemStack);
        return Optional.of("<yellow>" + reward.amount() + "x</yellow> " + display);
    }

    private int damageRank(double damage) {
        if (damage >= damageForRankOne) {
            return 1;
        }
        if (damage >= damageForRankTwo) {
            return 2;
        }
        if (damage >= damageForRankThree) {
            return 3;
        }
        return 4;
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

    private void consumeItem(Player player, String itemId, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < player.getInventory().getStorageContents().length && remaining > 0; slot++) {
            ItemStack itemStack = player.getInventory().getItem(slot);
            if (!matches(itemStack, itemId)) {
                continue;
            }
            int removed = Math.min(remaining, itemStack.getAmount());
            itemStack.setAmount(itemStack.getAmount() - removed);
            remaining -= removed;
            if (itemStack.getAmount() <= 0) {
                player.getInventory().setItem(slot, null);
            }
        }
    }

    private boolean matches(ItemStack itemStack, String itemId) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return false;
        }
        return customItems.definition(itemStack)
                .map(definition -> definition.id().equalsIgnoreCase(itemId))
                .orElse(false);
    }

    private void giveItem(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private List<TextService.TextPlaceholder> altarPlaceholders(SkyBlockProfile profile) {
        return List.of(
                TextService.raw("eyes", Integer.toString(profile.placedDragonEyes())),
                TextService.raw("eyes_required", Integer.toString(eyesRequired)),
                TextService.raw("eyes_needed", Integer.toString(Math.max(0, eyesRequired - profile.placedDragonEyes())))
        );
    }

    private List<TextService.TextPlaceholder> dragonPlaceholders(SkyBlockProfile profile, DragonDefinition dragon) {
        return List.of(
                TextService.raw("id", dragon.id()),
                TextService.parsed("dragon", dragon.displayName()),
                TextService.raw("health", text.formatNumber(dragon.health())),
                TextService.raw("combat_xp", text.formatNumber(dragon.combatXp())),
                TextService.raw("kills", Integer.toString(profile.dragonKills(dragon.id()))),
                TextService.raw("spawn_weight", text.formatNumber(dragon.spawnWeight()))
        );
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
