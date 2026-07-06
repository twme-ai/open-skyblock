package io.github.openskyblock.trophyfish;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CollectionService;
import io.github.openskyblock.service.SkillService;
import io.github.openskyblock.service.SkillType;
import io.github.openskyblock.stats.StatService;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class TrophyFishService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final SkillService skills;
    private final CollectionService collections;
    private final StatService stats;
    private final NamespacedKey fishIdKey;
    private final NamespacedKey tierKey;
    private final Map<String, TrophyFishDefinition> fish = new HashMap<>();
    private final Map<TrophyFishTier, TrophyTierDefinition> tiers = new EnumMap<>(TrophyFishTier.class);
    private boolean replaceCaughtItem = true;
    private double maxTrophyFishChance = 200.0D;
    private String itemDisplayName = "<tier> <fish>";
    private List<String> defaultLore = List.of();

    public TrophyFishService(JavaPlugin plugin, ConfigService configService, TextService text, ProfileManager profiles, SkillService skills, CollectionService collections, StatService stats) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.skills = skills;
        this.collections = collections;
        this.stats = stats;
        this.fishIdKey = new NamespacedKey(plugin, "trophy_fish_id");
        this.tierKey = new NamespacedKey(plugin, "trophy_fish_tier");
    }

    public void reload() {
        replaceCaughtItem = configService.trophyFish().getBoolean("settings.replace-caught-item", true);
        maxTrophyFishChance = Math.max(0.0D, configService.trophyFish().getDouble("settings.max-trophy-fish-chance", 200.0D));
        itemDisplayName = configService.trophyFish().getString("settings.item-display-name", "<tier> <fish>");
        defaultLore = List.copyOf(configService.trophyFish().getStringList("settings.default-lore"));
        loadTiers();
        loadFish();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.trophy-fishing", true);
    }

    public List<TrophyFishDefinition> definitions() {
        return fish.values().stream()
                .sorted(Comparator.comparingInt(TrophyFishDefinition::requiredFishingLevel).thenComparing(TrophyFishDefinition::id))
                .toList();
    }

    public List<String> fishIds() {
        return definitions().stream().map(TrophyFishDefinition::id).toList();
    }

    public List<TrophyTierDefinition> tierDefinitions() {
        return Arrays.stream(TrophyFishTier.values())
                .map(tiers::get)
                .filter(definition -> definition != null)
                .toList();
    }

    public boolean tryCatch(Player player, Entity caughtEntity) {
        if (!enabled() || player == null) {
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        int fishingLevel = skills.level(SkillType.FISHING, profile.skillXp(SkillType.FISHING));
        TrophyFishDefinition definition = selectFish(player, fishingLevel).orElse(null);
        if (definition == null) {
            return false;
        }
        TrophyTierDefinition tier = selectTier().orElse(null);
        if (tier == null) {
            return false;
        }
        ItemStack trophyItem = createItem(definition, tier);
        if (replaceCaughtItem && caughtEntity instanceof Item caughtItem) {
            caughtItem.setItemStack(trophyItem);
        } else {
            giveItem(player, trophyItem);
        }
        profile.addTrophyFish(definition.id(), tier.tier().name(), 1L);
        if (definition.skillXp() > 0.0D) {
            skills.addXp(player, SkillType.FISHING, definition.skillXp());
        }
        if (!definition.collectionId().isBlank() && definition.collectionAmount() > 0L) {
            collections.addProgress(player, definition.collectionId(), definition.collectionAmount());
        }
        text.send(player, "commands.trophy-fish-caught", List.of(
                TextService.parsed("fish", definition.displayName()),
                TextService.parsed("tier", tier.displayName()),
                TextService.raw("total", text.formatNumber(profile.trophyFish(definition.id(), tier.tier().name())))
        ));
        return true;
    }

    public boolean isTrophyFish(Entity entity) {
        if (!(entity instanceof Item item)) {
            return false;
        }
        ItemStack itemStack = item.getItemStack();
        if (!itemStack.hasItemMeta()) {
            return false;
        }
        return itemStack.getItemMeta().getPersistentDataContainer().has(fishIdKey, PersistentDataType.STRING);
    }

    public void sendSummary(Player player) {
        if (!enabled()) {
            text.send(player, "commands.trophy-fish-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        int fishingLevel = skills.level(SkillType.FISHING, profile.skillXp(SkillType.FISHING));
        text.send(player, "commands.trophy-fish-summary", List.of(
                TextService.raw("multiplier", text.formatNumber(effectiveMultiplier(player))),
                TextService.raw("bonus", text.formatNumber(effectiveBonus(player))),
                TextService.raw("fishing_level", Integer.toString(fishingLevel)),
                TextService.raw("eligible", Integer.toString(eligibleFish(fishingLevel).size())),
                TextService.raw("total", text.formatNumber(profile.trophyFishTotal()))
        ));
    }

    public void sendList(Player player) {
        if (!enabled()) {
            text.send(player, "commands.trophy-fish-disabled");
            return;
        }
        if (fish.isEmpty()) {
            text.send(player, "commands.trophy-fish-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        double multiplier = effectiveMultiplier(player);
        text.send(player, "commands.trophy-fish-list-header");
        for (TrophyFishDefinition definition : definitions()) {
            text.send(player, "commands.trophy-fish-list-line", List.of(
                    TextService.raw("id", definition.id()),
                    TextService.parsed("fish", definition.displayName()),
                    TextService.raw("required_level", Integer.toString(definition.requiredFishingLevel())),
                    TextService.raw("chance", text.formatNumber(Math.min(100.0D, definition.chance() * multiplier))),
                    TextService.raw("total", text.formatNumber(profile.trophyFishTotal(definition.id())))
            ));
        }
    }

    public void sendTiers(Player player) {
        if (!enabled()) {
            text.send(player, "commands.trophy-fish-disabled");
            return;
        }
        text.send(player, "commands.trophy-fish-tier-header");
        for (TrophyTierDefinition tier : tierDefinitions()) {
            text.send(player, "commands.trophy-fish-tier-line", List.of(
                    TextService.raw("tier_id", tier.tier().name()),
                    TextService.parsed("tier", tier.displayName()),
                    TextService.raw("chance", text.formatNumber(tier.chance()))
            ));
        }
    }

    private void loadTiers() {
        tiers.clear();
        for (TrophyFishTier tier : TrophyFishTier.values()) {
            ConfigurationSection section = configService.trophyFish().getConfigurationSection("tiers." + tier.name());
            tiers.put(tier, new TrophyTierDefinition(
                    tier,
                    section == null ? defaultTierName(tier) : section.getString("display-name", defaultTierName(tier)),
                    Math.max(0.0D, Math.min(100.0D, section == null ? defaultTierChance(tier) : section.getDouble("chance", defaultTierChance(tier))))
            ));
        }
    }

    private void loadFish() {
        fish.clear();
        ConfigurationSection section = configService.trophyFish().getConfigurationSection("fish");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection fishSection = section.getConfigurationSection(id);
            if (fishSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            Material material = Material.matchMaterial(fishSection.getString("material", "COD"));
            if (material == null || material.isAir()) {
                material = Material.COD;
            }
            String collectionId = fishSection.getString("collection", "");
            fish.put(normalized, new TrophyFishDefinition(
                    normalized,
                    fishSection.getString("display-name", normalized),
                    material,
                    Math.max(0, fishSection.getInt("required-fishing-level", 0)),
                    Math.max(0.0D, fishSection.getDouble("chance", 1.0D)),
                    Math.max(0.0D, fishSection.getDouble("skill-xp", 0.0D)),
                    collectionId == null || collectionId.isBlank() ? "" : collectionId.toUpperCase(Locale.ROOT),
                    Math.max(0L, fishSection.getLong("collection-amount", 0L)),
                    List.copyOf(fishSection.getStringList("lore"))
            ));
        }
    }

    private Optional<TrophyFishDefinition> selectFish(Player player, int fishingLevel) {
        List<TrophyFishDefinition> eligible = eligibleFish(fishingLevel).stream()
                .sorted(Comparator.comparingDouble(TrophyFishDefinition::chance).thenComparing(TrophyFishDefinition::id))
                .toList();
        double multiplier = effectiveMultiplier(player);
        for (TrophyFishDefinition definition : eligible) {
            double chance = Math.min(100.0D, definition.chance() * multiplier);
            if (ThreadLocalRandom.current().nextDouble(100.0D) < chance) {
                return Optional.of(definition);
            }
        }
        return Optional.empty();
    }

    private List<TrophyFishDefinition> eligibleFish(int fishingLevel) {
        return definitions().stream()
                .filter(definition -> fishingLevel >= definition.requiredFishingLevel())
                .toList();
    }

    private Optional<TrophyTierDefinition> selectTier() {
        for (TrophyFishTier tier : TrophyFishTier.values()) {
            TrophyTierDefinition definition = tiers.get(tier);
            if (definition != null && ThreadLocalRandom.current().nextDouble(100.0D) < definition.chance()) {
                return Optional.of(definition);
            }
        }
        return Optional.ofNullable(tiers.get(TrophyFishTier.BRONZE));
    }

    private ItemStack createItem(TrophyFishDefinition definition, TrophyTierDefinition tier) {
        ItemStack itemStack = new ItemStack(definition.material());
        ItemMeta meta = itemStack.getItemMeta();
        List<TextService.TextPlaceholder> placeholders = itemPlaceholders(definition, tier);
        meta.displayName(text.deserialize(itemDisplayName, placeholders));
        List<Component> lore = new ArrayList<>();
        List<String> loreLines = definition.lore().isEmpty() ? defaultLore : definition.lore();
        for (String line : loreLines) {
            lore.add(text.deserialize(line, placeholders));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(fishIdKey, PersistentDataType.STRING, definition.id());
        meta.getPersistentDataContainer().set(tierKey, PersistentDataType.STRING, tier.tier().name());
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    private List<TextService.TextPlaceholder> itemPlaceholders(TrophyFishDefinition definition, TrophyTierDefinition tier) {
        return List.of(
                TextService.raw("id", definition.id()),
                TextService.parsed("fish", definition.displayName()),
                TextService.raw("tier_id", tier.tier().name()),
                TextService.parsed("tier", tier.displayName())
        );
    }

    private void giveItem(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private double effectiveBonus(Player player) {
        return Math.min(maxTrophyFishChance, Math.max(0.0D, stats.snapshot(player).stat("trophy_fish_chance")));
    }

    private double effectiveMultiplier(Player player) {
        return 1.0D + (effectiveBonus(player) / 100.0D);
    }

    private String defaultTierName(TrophyFishTier tier) {
        return switch (tier) {
            case DIAMOND -> "<aqua><bold>Diamond</bold></aqua>";
            case GOLD -> "<gold>Gold</gold>";
            case SILVER -> "<gray>Silver</gray>";
            case BRONZE -> "<#cd7f32>Bronze</#cd7f32>";
        };
    }

    private double defaultTierChance(TrophyFishTier tier) {
        return switch (tier) {
            case DIAMOND -> 0.2D;
            case GOLD -> 2.5D;
            case SILVER -> 15.0D;
            case BRONZE -> 100.0D;
        };
    }
}
