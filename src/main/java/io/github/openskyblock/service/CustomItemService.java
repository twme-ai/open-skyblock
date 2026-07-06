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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class CustomItemService {
    private final ConfigService configService;
    private final TextService text;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey rarityKey;
    private final NamespacedKey recombobulatedKey;
    private final NamespacedKey soulboundTypeKey;
    private final NamespacedKey soulboundOwnerKey;
    private final NamespacedKey soulboundOwnerNameKey;
    private final Map<String, CustomItemDefinition> definitions = new HashMap<>();
    private ReforgeService reforgeService;
    private EnchantmentService enchantmentService;
    private StarService starService;
    private GemstoneService gemstoneService;

    public CustomItemService(JavaPlugin plugin, ConfigService configService, TextService text) {
        this.configService = configService;
        this.text = text;
        this.itemIdKey = new NamespacedKey(plugin, "item_id");
        this.rarityKey = new NamespacedKey(plugin, "rarity_override");
        this.recombobulatedKey = new NamespacedKey(plugin, "recombobulated");
        this.soulboundTypeKey = new NamespacedKey(plugin, "soulbound_type");
        this.soulboundOwnerKey = new NamespacedKey(plugin, "soulbound_owner");
        this.soulboundOwnerNameKey = new NamespacedKey(plugin, "soulbound_owner_name");
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
                Map<String, Double> parameters = new HashMap<>();
                ConfigurationSection parameterSection = abilitySection.getConfigurationSection("parameters");
                if (parameterSection != null) {
                    for (String parameter : parameterSection.getKeys(false)) {
                        parameters.put(parameter.toLowerCase(Locale.ROOT).replace('-', '_'), parameterSection.getDouble(parameter, 0.0D));
                    }
                }
                ability = new AbilityDefinition(
                        abilitySection.getString("name", ""),
                        abilitySection.getString("type", ""),
                        abilitySection.getString("action", ""),
                        abilitySection.getStringList("lines"),
                        abilitySection.getDouble("mana-cost", 0.0D),
                        abilitySection.getInt("cooldown-seconds", 0),
                        Map.copyOf(parameters)
                );
            }
            definitions.put(id.toUpperCase(Locale.ROOT), new CustomItemDefinition(
                    id.toUpperCase(Locale.ROOT),
                    material == null ? Material.STONE : material,
                    itemSection.getString("display-name", id),
                    itemSection.getString("category", "ITEM"),
                    itemSection.getString("armor-set", ""),
                    itemSection.getString("equipment-slot", ""),
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

    public Rarity rarity(ItemStack itemStack, CustomItemDefinition definition) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return definition.rarity();
        }
        String stored = itemStack.getItemMeta().getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
        if (stored == null || stored.isBlank()) {
            return definition.rarity();
        }
        return Rarity.parse(stored);
    }

    public boolean recombobulated(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return false;
        }
        Byte value = itemStack.getItemMeta().getPersistentDataContainer().get(recombobulatedKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    public boolean soulbound(ItemStack itemStack) {
        return !soulboundType(itemStack).isBlank();
    }

    public String soulboundType(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return "";
        }
        String value = itemStack.getItemMeta().getPersistentDataContainer().get(soulboundTypeKey, PersistentDataType.STRING);
        return value == null ? "" : normalizeSoulboundType(value);
    }

    public List<String> soulboundTypes() {
        Set<String> types = new LinkedHashSet<>();
        types.add("PLAYER");
        types.add("COOP");
        types.add(soulboundDefaultType());
        ConfigurationSection section = configService.items().getConfigurationSection("soulbound.display-names");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                String normalized = normalizeSoulboundType(key);
                if (!normalized.isBlank()) {
                    types.add(normalized);
                }
            }
        }
        return types.stream().filter(type -> !type.isBlank()).toList();
    }

    public boolean recombobulateHeld(Player player) {
        if (!rarityUpgradesEnabled()) {
            text.send(player, "commands.recombobulate-disabled");
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition definition = definition(held).orElse(null);
        if (definition == null) {
            text.send(player, "commands.recombobulate-held-missing");
            return false;
        }
        if (held.getAmount() != 1) {
            text.send(player, "commands.recombobulate-single-item");
            return false;
        }
        if (recombobulated(held)) {
            text.send(player, "commands.recombobulate-already", recombobulatePlaceholders(held, definition, rarity(held, definition)));
            return false;
        }
        if (!rarityUpgradeAllowed(definition)) {
            text.send(player, "commands.recombobulate-not-applicable", recombobulatePlaceholders(held, definition, rarity(held, definition)));
            return false;
        }
        Rarity current = rarity(held, definition);
        Rarity upgraded = current.next();
        if (upgraded == current || upgraded.ordinal() > maxRarity(current).ordinal()) {
            text.send(player, "commands.recombobulate-maxed", recombobulatePlaceholders(held, definition, current));
            return false;
        }
        String requiredItemId = recombobulatorItemId();
        int requiredAmount = recombobulatorAmount();
        if (!requiredItemId.isBlank() && countRequiredItems(player, requiredItemId) < requiredAmount) {
            text.send(player, "commands.recombobulate-missing-item", recombobulatePlaceholders(held, definition, upgraded));
            return false;
        }
        if (!requiredItemId.isBlank()) {
            consumeRequiredItems(player, requiredItemId, requiredAmount);
        }
        ItemMeta meta = held.getItemMeta();
        meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, upgraded.name());
        meta.getPersistentDataContainer().set(recombobulatedKey, PersistentDataType.BYTE, (byte) 1);
        held.setItemMeta(meta);
        refreshItem(held);
        text.send(player, "commands.recombobulate-success", recombobulatePlaceholders(held, definition, upgraded));
        return true;
    }

    public boolean soulbindHeld(Player player, String rawType) {
        if (!soulbindingEnabled()) {
            text.send(player, "commands.soulbind-disabled");
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition definition = definition(held).orElse(null);
        if (definition == null) {
            text.send(player, "commands.soulbind-held-missing");
            return false;
        }
        if (held.getAmount() != 1) {
            text.send(player, "commands.soulbind-single-item");
            return false;
        }
        if (soulbound(held)) {
            text.send(player, "commands.soulbind-already", soulboundPlaceholders(held, definition, soulboundType(held)));
            return false;
        }
        String type = normalizeSoulboundType(rawType == null || rawType.isBlank() ? soulboundDefaultType() : rawType);
        if (!soulboundTypes().contains(type)) {
            text.send(player, "commands.soulbind-unknown-type", List.of(
                    TextService.raw("type", rawType == null ? "" : rawType),
                    TextService.raw("types", String.join(", ", soulboundTypes()))
            ));
            return false;
        }
        if (!soulbindingAllowed(definition)) {
            text.send(player, "commands.soulbind-not-applicable", soulboundPlaceholders(held, definition, type));
            return false;
        }
        ItemMeta meta = held.getItemMeta();
        meta.getPersistentDataContainer().set(soulboundTypeKey, PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(soulboundOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        meta.getPersistentDataContainer().set(soulboundOwnerNameKey, PersistentDataType.STRING, player.getName());
        held.setItemMeta(meta);
        refreshItem(held);
        text.send(player, "commands.soulbind-success", soulboundPlaceholders(held, definition, type));
        return true;
    }

    public boolean applySoulbound(ItemStack itemStack, Player owner, String rawType) {
        if (definition(itemStack).isEmpty() || owner == null) {
            return false;
        }
        String type = normalizeSoulboundType(rawType == null || rawType.isBlank() ? soulboundDefaultType() : rawType);
        ItemMeta meta = itemStack.getItemMeta();
        meta.getPersistentDataContainer().set(soulboundTypeKey, PersistentDataType.STRING, type);
        meta.getPersistentDataContainer().set(soulboundOwnerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        meta.getPersistentDataContainer().set(soulboundOwnerNameKey, PersistentDataType.STRING, owner.getName());
        itemStack.setItemMeta(meta);
        refreshItem(itemStack);
        return true;
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
        Rarity effectiveRarity = rarity(itemStack, definition);
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
        if (definition.equipmentSlot() != null && !definition.equipmentSlot().isBlank()) {
            lines.add(text.message("items.equipment-slot-line", List.of(TextService.raw("slot", definition.equipmentSlot()))));
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
        if (recombobulated(itemStack)) {
            lines.add(text.message("items.recombobulated-line"));
        }
        if (soulbound(itemStack)) {
            lines.add(text.message("items.soulbound-line", soulboundPlaceholders(itemStack, definition, soulboundType(itemStack))));
        }
        lines.add(Component.empty());
        String rarityFormat = text.rawMessage("items.rarity-line")
                .replace("<rarity_color>", effectiveRarity.colorTag())
                .replace("</rarity_color>", "");
        lines.add(text.deserialize(rarityFormat, List.of(
                TextService.raw("rarity", effectiveRarity.name()),
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

    private boolean rarityUpgradesEnabled() {
        return configService.items().getBoolean("rarity-upgrades.enabled", true);
    }

    private boolean soulbindingEnabled() {
        return configService.items().getBoolean("soulbound.enabled", true);
    }

    private String recombobulatorItemId() {
        return configService.items().getString("rarity-upgrades.required-item", "RECOMBOBULATOR_3000").toUpperCase(Locale.ROOT);
    }

    private int recombobulatorAmount() {
        return Math.max(1, configService.items().getInt("rarity-upgrades.required-amount", 1));
    }

    private boolean rarityUpgradeAllowed(CustomItemDefinition definition) {
        List<String> categories = configService.items().getStringList("rarity-upgrades.allowed-categories");
        if (categories.isEmpty()) {
            return true;
        }
        String category = definition.category().toUpperCase(Locale.ROOT);
        return categories.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(category) || allowed.equalsIgnoreCase("ALL"));
    }

    private boolean soulbindingAllowed(CustomItemDefinition definition) {
        List<String> categories = configService.items().getStringList("soulbound.allowed-categories");
        if (categories.isEmpty()) {
            return true;
        }
        String category = definition.category().toUpperCase(Locale.ROOT);
        return categories.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(category) || allowed.equalsIgnoreCase("ALL"));
    }

    private Rarity maxRarity(Rarity current) {
        if (current == Rarity.SPECIAL || current == Rarity.VERY_SPECIAL) {
            return Rarity.parse(configService.items().getString("rarity-upgrades.max-special-rarity", "VERY_SPECIAL"));
        }
        return Rarity.parse(configService.items().getString("rarity-upgrades.max-rarity", "MYTHIC"));
    }

    private List<TextService.TextPlaceholder> recombobulatePlaceholders(ItemStack itemStack, CustomItemDefinition definition, Rarity targetRarity) {
        String requiredItemId = recombobulatorItemId();
        String requiredItem = definition(requiredItemId)
                .map(CustomItemDefinition::displayName)
                .orElse(requiredItemId);
        return List.of(
                TextService.parsed("item", definition.displayName()),
                TextService.raw("category", definition.category().toUpperCase(Locale.ROOT)),
                TextService.raw("current_rarity", rarity(itemStack, definition).name()),
                TextService.raw("target_rarity", targetRarity.name()),
                TextService.raw("required_amount", Integer.toString(recombobulatorAmount())),
                TextService.parsed("required_item", requiredItem)
        );
    }

    private List<TextService.TextPlaceholder> soulboundPlaceholders(ItemStack itemStack, CustomItemDefinition definition, String type) {
        String normalizedType = normalizeSoulboundType(type);
        return List.of(
                TextService.parsed("item", definition.displayName()),
                TextService.raw("category", definition.category().toUpperCase(Locale.ROOT)),
                TextService.raw("type_id", normalizedType),
                TextService.parsed("type", soulboundDisplayName(normalizedType)),
                TextService.raw("owner", soulboundOwnerName(itemStack))
        );
    }

    private String soulboundDefaultType() {
        String configured = configService.items().getString("soulbound.default-type", "PLAYER");
        String normalized = normalizeSoulboundType(configured);
        return normalized.isBlank() ? "PLAYER" : normalized;
    }

    private String soulboundDisplayName(String type) {
        String normalized = normalizeSoulboundType(type);
        String configured = configService.items().getString("soulbound.display-names." + normalized);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        if (normalized.equals("COOP")) {
            return "Co-op Soulbound";
        }
        String readable = normalized.toLowerCase(Locale.ROOT).replace('_', ' ');
        if (readable.isBlank()) {
            return "Soulbound";
        }
        return Character.toUpperCase(readable.charAt(0)) + readable.substring(1) + " Soulbound";
    }

    private String soulboundOwnerName(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return "";
        }
        String owner = itemStack.getItemMeta().getPersistentDataContainer().get(soulboundOwnerNameKey, PersistentDataType.STRING);
        return owner == null || owner.isBlank() ? "Unknown" : owner;
    }

    private String normalizeSoulboundType(String raw) {
        if (raw == null) {
            return "";
        }
        String normalized = raw.trim().replace('-', '_').replace(' ', '_').toUpperCase(Locale.ROOT);
        if (normalized.equals("CO_OP") || normalized.equals("COOP")) {
            return "COOP";
        }
        return normalized;
    }

    private int countRequiredItems(Player player, String requiredItemId) {
        int heldSlot = player.getInventory().getHeldItemSlot();
        int amount = 0;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (slot == heldSlot) {
                continue;
            }
            ItemStack itemStack = player.getInventory().getItem(slot);
            if (definition(itemStack).map(item -> item.id().equalsIgnoreCase(requiredItemId)).orElse(false)) {
                amount += itemStack.getAmount();
            }
        }
        return amount;
    }

    private void consumeRequiredItems(Player player, String requiredItemId, int amount) {
        int remaining = amount;
        int heldSlot = player.getInventory().getHeldItemSlot();
        for (int slot = 0; slot < player.getInventory().getSize() && remaining > 0; slot++) {
            if (slot == heldSlot) {
                continue;
            }
            ItemStack itemStack = player.getInventory().getItem(slot);
            if (!definition(itemStack).map(item -> item.id().equalsIgnoreCase(requiredItemId)).orElse(false)) {
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
}
