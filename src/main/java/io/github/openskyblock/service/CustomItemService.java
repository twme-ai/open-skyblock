package io.github.openskyblock.service;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.enchant.EnchantmentService;
import io.github.openskyblock.gemstone.GemstoneService;
import io.github.openskyblock.reforge.ReforgeDefinition;
import io.github.openskyblock.reforge.ReforgeService;
import io.github.openskyblock.star.StarService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomItemService {
    private final ConfigService configService;
    private final TextService text;
    private final NamespacedKey itemIdKey;
    private final Map<String, CustomItemDefinition> definitions = new HashMap<>();
    private ReforgeService reforgeService;
    private EnchantmentService enchantmentService;
    private StarService starService;
    private GemstoneService gemstoneService;

    public CustomItemService(JavaPlugin plugin, ConfigService configService, TextService text) {
        this.configService = configService;
        this.text = text;
        this.itemIdKey = new NamespacedKey(plugin, "item_id");
    }

    public void reforgeService(ReforgeService reforgeService) {
        this.reforgeService = reforgeService;
    }

    public void enchantmentService(EnchantmentService enchantmentService) {
        this.enchantmentService = enchantmentService;
    }

    public void starService(StarService starService) {
        this.starService = starService;
    }

    public void gemstoneService(GemstoneService gemstoneService) {
        this.gemstoneService = gemstoneService;
    }

    public void reload() {
        definitions.clear();
        ConfigurationSection section = configService.items().getConfigurationSection("items");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection itemSection = section.getConfigurationSection(id);
            if (itemSection == null) {
                continue;
            }
            Material material = Material.matchMaterial(itemSection.getString("material", "STONE"));
            Map<String, Double> stats = new HashMap<>();
            ConfigurationSection statsSection = itemSection.getConfigurationSection("stats");
            if (statsSection != null) {
                for (String stat : statsSection.getKeys(false)) {
                    stats.put(stat.toLowerCase(Locale.ROOT), statsSection.getDouble(stat, 0.0D));
                }
            }
            AbilityDefinition ability = null;
            ConfigurationSection abilitySection = itemSection.getConfigurationSection("ability");
            if (abilitySection != null) {
                ability = new AbilityDefinition(
                        abilitySection.getString("name", ""),
                        abilitySection.getString("type", ""),
                        abilitySection.getStringList("lines"),
                        abilitySection.getDouble("mana-cost", 0.0D),
                        abilitySection.getInt("cooldown-seconds", 0)
                );
            }
            definitions.put(id.toUpperCase(Locale.ROOT), new CustomItemDefinition(
                    id.toUpperCase(Locale.ROOT),
                    material == null ? Material.STONE : material,
                    itemSection.getString("display-name", id),
                    itemSection.getString("category", "ITEM"),
                    itemSection.getString("armor-set", ""),
                    Rarity.parse(itemSection.getString("rarity", "COMMON")),
                    itemSection.getStringList("lore"),
                    stats,
                    ability
            ));
        }
    }

    public Optional<CustomItemDefinition> definition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<CustomItemDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(CustomItemDefinition::id))
                .toList();
    }

    public ItemStack createItem(CustomItemDefinition definition) {
        ItemStack itemStack = new ItemStack(definition.material());
        ItemMeta meta = itemStack.getItemMeta();
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, definition.id());
        itemStack.setItemMeta(meta);
        refreshItem(itemStack);
        return itemStack;
    }

    public void refreshItem(ItemStack itemStack) {
        CustomItemDefinition definition = definition(itemStack).orElse(null);
        if (definition == null) {
            return;
        }
        ItemMeta meta = itemStack.getItemMeta();
        meta.displayName(displayName(definition, itemStack));
        meta.lore(lore(definition, itemStack));
        itemStack.setItemMeta(meta);
    }

    public Optional<CustomItemDefinition> definition(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return Optional.empty();
        }
        String id = itemStack.getItemMeta().getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
        return definition(id);
    }

    private Component displayName(CustomItemDefinition definition, ItemStack itemStack) {
        ReforgeDefinition reforge = reforge(itemStack);
        String suffix = starService == null ? "" : starService.suffix(itemStack);
        if (reforge == null) {
            return text.deserialize(definition.displayName() + suffix);
        }
        String raw = text.rawMessage("items.reforged-display-name")
                .replace("<reforge_prefix>", reforge.prefix())
                .replace("<item_name>", definition.displayName());
        return text.deserialize(raw + suffix);
    }

    private List<Component> lore(CustomItemDefinition definition, ItemStack itemStack) {
        List<Component> lines = new ArrayList<>();
        for (String line : definition.lore()) {
            lines.add(text.deserialize(line));
        }
        if (starService != null) {
            List<Component> starLore = starService.lore(itemStack, definition);
            if (!starLore.isEmpty()) {
                lines.add(Component.empty());
                lines.addAll(starLore);
            }
        }
        if (gemstoneService != null) {
            List<Component> gemstoneLore = gemstoneService.lore(itemStack, definition);
            if (!gemstoneLore.isEmpty()) {
                lines.add(Component.empty());
                lines.addAll(gemstoneLore);
            }
        }
        if (enchantmentService != null) {
            List<Component> enchantmentLore = enchantmentService.lore(itemStack);
            if (!enchantmentLore.isEmpty()) {
                lines.add(Component.empty());
                lines.addAll(enchantmentLore);
            }
        }
        if (!definition.stats().isEmpty()) {
            lines.add(Component.empty());
            definition.stats().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> lines.add(text.message("items.stat-line", List.of(
                            TextService.raw("stat", statName(entry.getKey())),
                            TextService.raw("value", text.formatNumber(entry.getValue()))
                    ))));
        }
        Map<String, Double> reforgeStats = reforgeStats(itemStack, definition);
        if (!reforgeStats.isEmpty()) {
            lines.add(Component.empty());
            ReforgeDefinition reforge = reforge(itemStack);
            if (reforge != null) {
                lines.add(text.message("items.reforge-line", List.of(TextService.parsed("reforge", reforge.displayName()))));
            }
            reforgeStats.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> lines.add(text.message("items.reforge-stat-line", List.of(
                            TextService.raw("stat", statName(entry.getKey())),
                            TextService.raw("value", text.formatNumber(entry.getValue()))
                    ))));
        }
        if (definition.armorSet() != null && !definition.armorSet().isBlank()) {
            lines.add(text.message("items.armor-set-line", List.of(TextService.raw("set", definition.armorSet()))));
        }
        AbilityDefinition ability = definition.ability();
        if (ability != null && !ability.name().isBlank()) {
            lines.add(Component.empty());
            lines.add(text.message("items.ability-header", List.of(
                    TextService.raw("ability", ability.name()),
                    TextService.raw("type", ability.type())
            )));
            for (String abilityLine : ability.lines()) {
                lines.add(text.message("items.ability-line", List.of(TextService.raw("line", abilityLine))));
            }
            if (ability.manaCost() > 0.0D) {
                lines.add(text.message("items.mana-cost-line", List.of(
                        TextService.raw("mana_cost", text.formatNumber(ability.manaCost()))
                )));
            }
            if (ability.cooldownSeconds() > 0) {
                lines.add(text.message("items.cooldown-line", List.of(
                        TextService.raw("cooldown_seconds", Integer.toString(ability.cooldownSeconds()))
                )));
            }
        }
        lines.add(Component.empty());
        String rarityFormat = text.rawMessage("items.rarity-line")
                .replace("<rarity_color>", definition.rarity().colorTag())
                .replace("</rarity_color>", "");
        lines.add(text.deserialize(rarityFormat, List.of(
                TextService.raw("rarity", definition.rarity().name()),
                TextService.raw("category", definition.category().toUpperCase(Locale.ROOT))
        )));
        return lines;
    }

    private ReforgeDefinition reforge(ItemStack itemStack) {
        if (reforgeService == null) {
            return null;
        }
        return reforgeService.definition(itemStack).orElse(null);
    }

    private Map<String, Double> reforgeStats(ItemStack itemStack, CustomItemDefinition definition) {
        if (reforgeService == null) {
            return Map.of();
        }
        return reforgeService.stats(itemStack, definition);
    }

    private String statName(String stat) {
        String configured = configService.messages().getString("items.stat-labels." + stat);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String normalized = stat.replace('-', ' ').replace('_', ' ');
        if (normalized.isBlank()) {
            return stat;
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }
}
