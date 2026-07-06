package io.github.openskyblock.enchant;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.stats.StatSnapshot;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class EnchantmentService {
    private final ConfigService configService;
    private final TextService text;
    private final EconomyService economy;
    private final CustomItemService customItems;
    private final NamespacedKey enchantmentsKey;
    private final Map<String, SkyBlockEnchantmentDefinition> definitions = new HashMap<>();

    public EnchantmentService(JavaPlugin plugin, ConfigService configService, TextService text, EconomyService economy, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.economy = economy;
        this.customItems = customItems;
        this.enchantmentsKey = new NamespacedKey(plugin, "enchantments");
    }

    public void reload() {
        definitions.clear();
        ConfigurationSection section = configService.enchantments().getConfigurationSection("enchantments");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection enchantmentSection = section.getConfigurationSection(id);
            if (enchantmentSection == null) {
                continue;
            }
            String normalizedId = id.toUpperCase(Locale.ROOT);
            definitions.put(normalizedId, new SkyBlockEnchantmentDefinition(
                    normalizedId,
                    enchantmentSection.getString("display-name", normalizedId),
                    enchantmentSection.getBoolean("ultimate", false),
                    Math.max(1, enchantmentSection.getInt("max-level", 1)),
                    categories(enchantmentSection.getStringList("allowed-categories")),
                    Math.max(0.0D, enchantmentSection.getDouble("cost-multiplier", 1.0D)),
                    enchantmentSection.getStringList("lore"),
                    stats(enchantmentSection.getConfigurationSection("stats-per-level"))
            ));
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.enchantments", true);
    }

    public Optional<SkyBlockEnchantmentDefinition> definition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<SkyBlockEnchantmentDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(SkyBlockEnchantmentDefinition::id))
                .toList();
    }

    public List<SkyBlockEnchantmentDefinition> applicableDefinitions(CustomItemDefinition itemDefinition) {
        if (itemDefinition == null) {
            return List.of();
        }
        return definitions().stream()
                .filter(definition -> isApplicable(definition, itemDefinition))
                .toList();
    }

    public Map<String, Integer> enchantments(ItemStack itemStack) {
        if (!enabled() || itemStack == null || !itemStack.hasItemMeta()) {
            return Map.of();
        }
        String raw = itemStack.getItemMeta().getPersistentDataContainer().get(enchantmentsKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, Integer> enchantments = new LinkedHashMap<>();
        for (String part : raw.split(";")) {
            String[] pieces = part.split(":", 2);
            if (pieces.length != 2) {
                continue;
            }
            String id = pieces[0].toUpperCase(Locale.ROOT);
            SkyBlockEnchantmentDefinition definition = definition(id).orElse(null);
            if (definition == null) {
                continue;
            }
            try {
                int level = Math.max(1, Math.min(definition.maxLevel(), Integer.parseInt(pieces[1])));
                enchantments.put(id, level);
            } catch (NumberFormatException ignored) {
            }
        }
        return enchantments;
    }

    public Map<String, Double> stats(ItemStack itemStack, CustomItemDefinition itemDefinition) {
        if (!enabled() || itemDefinition == null) {
            return Map.of();
        }
        Map<String, Double> stats = new HashMap<>();
        for (Map.Entry<String, Integer> entry : enchantments(itemStack).entrySet()) {
            SkyBlockEnchantmentDefinition definition = definition(entry.getKey()).orElse(null);
            if (definition == null || !isApplicable(definition, itemDefinition)) {
                continue;
            }
            for (Map.Entry<String, Double> stat : definition.statsPerLevel().entrySet()) {
                stats.put(stat.getKey(), stats.getOrDefault(stat.getKey(), 0.0D) + stat.getValue() * entry.getValue());
            }
        }
        stats.entrySet().removeIf(entry -> Math.abs(entry.getValue()) <= 0.000001D);
        return stats;
    }

    public boolean applyHeld(Player player, String rawEnchantmentId, int requestedLevel) {
        if (!enabled()) {
            text.send(player, "commands.enchantment-disabled");
            return false;
        }
        SkyBlockEnchantmentDefinition definition = definition(rawEnchantmentId).orElse(null);
        if (definition == null) {
            text.send(player, "errors.unknown-enchantment", List.of(TextService.raw("enchantment", rawEnchantmentId)));
            return false;
        }
        int level = Math.max(1, Math.min(definition.maxLevel(), requestedLevel));
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition itemDefinition = customItems.definition(held).orElse(null);
        if (itemDefinition == null) {
            text.send(player, "commands.enchantment-held-missing");
            return false;
        }
        if (!isApplicable(definition, itemDefinition)) {
            text.send(player, "commands.enchantment-not-applicable", placeholders(definition, itemDefinition, level, cost(definition, itemDefinition, level)));
            return false;
        }
        Map<String, Integer> current = new LinkedHashMap<>(enchantments(held));
        if (current.getOrDefault(definition.id(), 0) == level) {
            text.send(player, "commands.enchantment-already", placeholders(definition, itemDefinition, level, cost(definition, itemDefinition, level)));
            return false;
        }
        if (definition.ultimate()) {
            SkyBlockEnchantmentDefinition existingUltimate = current.keySet().stream()
                    .map(this::definition)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(SkyBlockEnchantmentDefinition::ultimate)
                    .filter(existing -> !existing.id().equals(definition.id()))
                    .findFirst()
                    .orElse(null);
            if (existingUltimate != null) {
                text.send(player, "commands.enchantment-ultimate-conflict", List.of(
                        TextService.parsed("enchantment", definition.displayName()),
                        TextService.parsed("existing", existingUltimate.displayName())
                ));
                return false;
            }
        }
        double cost = cost(definition, itemDefinition, level);
        if (!economy.spendPurse(player, cost)) {
            text.send(player, "commands.enchantment-no-money", placeholders(definition, itemDefinition, level, cost));
            return false;
        }
        current.put(definition.id(), level);
        writeEnchantments(held, current);
        customItems.refreshItem(held);
        text.send(player, "commands.enchantment-applied", placeholders(definition, itemDefinition, level, cost));
        return true;
    }

    public boolean removeHeld(Player player, String rawEnchantmentId) {
        if (!enabled()) {
            text.send(player, "commands.enchantment-disabled");
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition itemDefinition = customItems.definition(held).orElse(null);
        if (itemDefinition == null) {
            text.send(player, "commands.enchantment-held-missing");
            return false;
        }
        SkyBlockEnchantmentDefinition definition = definition(rawEnchantmentId).orElse(null);
        if (definition == null) {
            text.send(player, "errors.unknown-enchantment", List.of(TextService.raw("enchantment", rawEnchantmentId)));
            return false;
        }
        Map<String, Integer> current = new LinkedHashMap<>(enchantments(held));
        Integer level = current.remove(definition.id());
        if (level == null) {
            text.send(player, "commands.enchantment-none", placeholders(definition, itemDefinition, 1, removeCost()));
            return false;
        }
        double cost = removeCost();
        if (!economy.spendPurse(player, cost)) {
            text.send(player, "commands.enchantment-no-money", placeholders(definition, itemDefinition, level, cost));
            return false;
        }
        writeEnchantments(held, current);
        customItems.refreshItem(held);
        text.send(player, "commands.enchantment-removed", placeholders(definition, itemDefinition, level, cost));
        return true;
    }

    public void sendList(CommandSender sender) {
        if (!enabled()) {
            text.send(sender, "commands.enchantment-disabled");
            return;
        }
        text.send(sender, "commands.enchantment-list-header");
        for (SkyBlockEnchantmentDefinition definition : definitions()) {
            text.send(sender, "commands.enchantment-list-line", List.of(
                    TextService.raw("id", definition.id()),
                    TextService.parsed("enchantment", definition.displayName()),
                    TextService.raw("max_level", Integer.toString(definition.maxLevel())),
                    TextService.raw("type", definition.ultimate() ? text.rawMessage("enchantments.ultimate-type") : text.rawMessage("enchantments.normal-type")),
                    TextService.raw("categories", String.join(", ", definition.allowedCategories()))
            ));
        }
    }

    public List<Component> lore(ItemStack itemStack) {
        return enchantments(itemStack).entrySet().stream()
                .map(entry -> definition(entry.getKey())
                        .map(definition -> text.message(definition.ultimate() ? "items.ultimate-enchantment-line" : "items.enchantment-line", List.of(
                                TextService.parsed("enchantment", definition.displayName()),
                                TextService.raw("level", levelLabel(entry.getValue()))
                        )))
                        .orElse(null))
                .filter(component -> component != null)
                .toList();
    }

    public boolean isApplicable(SkyBlockEnchantmentDefinition definition, CustomItemDefinition itemDefinition) {
        if (definition.allowedCategories().isEmpty() || definition.allowedCategories().contains("ALL")) {
            return true;
        }
        return definition.allowedCategories().contains(itemDefinition.category().toUpperCase(Locale.ROOT));
    }

    public double cost(SkyBlockEnchantmentDefinition definition, CustomItemDefinition itemDefinition, int level) {
        double base = Math.max(0.0D, configService.enchantments().getDouble("settings.base-costs." + itemDefinition.rarity().name(), 0.0D));
        return base * Math.max(0.0D, definition.costMultiplier()) * Math.max(1, level);
    }

    public double removeCost() {
        return Math.max(0.0D, configService.enchantments().getDouble("settings.remove-cost", 0.0D));
    }

    public List<String> levelSuggestions(String enchantmentId) {
        SkyBlockEnchantmentDefinition definition = definition(enchantmentId).orElse(null);
        if (definition == null) {
            return List.of("1", "2", "3", "4", "5");
        }
        return java.util.stream.IntStream.rangeClosed(1, definition.maxLevel())
                .mapToObj(Integer::toString)
                .toList();
    }

    private void writeEnchantments(ItemStack itemStack, Map<String, Integer> enchantments) {
        ItemMeta meta = itemStack.getItemMeta();
        if (enchantments.isEmpty()) {
            meta.getPersistentDataContainer().remove(enchantmentsKey);
        } else {
            String raw = enchantments.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + ":" + entry.getValue())
                    .collect(java.util.stream.Collectors.joining(";"));
            meta.getPersistentDataContainer().set(enchantmentsKey, PersistentDataType.STRING, raw);
        }
        itemStack.setItemMeta(meta);
    }

    public List<TextService.TextPlaceholder> placeholders(SkyBlockEnchantmentDefinition definition, CustomItemDefinition itemDefinition, int level, double cost) {
        return List.of(
                TextService.raw("enchantment_id", definition.id()),
                TextService.parsed("enchantment", definition.displayName()),
                TextService.raw("level", levelLabel(level)),
                TextService.raw("numeric_level", Integer.toString(level)),
                TextService.parsed("item", itemDefinition.displayName()),
                TextService.raw("category", itemDefinition.category().toUpperCase(Locale.ROOT)),
                TextService.raw("cost", text.formatNumber(cost))
        );
    }

    private Set<String> categories(List<String> rawCategories) {
        Set<String> categories = new LinkedHashSet<>();
        for (String category : rawCategories) {
            if (category != null && !category.isBlank()) {
                categories.add(category.toUpperCase(Locale.ROOT));
            }
        }
        return categories;
    }

    private Map<String, Double> stats(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Double> stats = new HashMap<>();
        for (String key : section.getKeys(false)) {
            stats.put(StatSnapshot.normalize(key), section.getDouble(key, 0.0D));
        }
        return stats;
    }

    public String levelLabel(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            case 10 -> "X";
            default -> Integer.toString(level);
        };
    }
}
