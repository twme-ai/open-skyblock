package io.github.openskyblock.reforge;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.service.Rarity;
import io.github.openskyblock.stats.StatSnapshot;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ReforgeService {
    private final ConfigService configService;
    private final TextService text;
    private final EconomyService economy;
    private final CustomItemService customItems;
    private final NamespacedKey reforgeKey;
    private final Map<String, ReforgeDefinition> definitions = new HashMap<>();

    public ReforgeService(JavaPlugin plugin, ConfigService configService, TextService text, EconomyService economy, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.economy = economy;
        this.customItems = customItems;
        this.reforgeKey = new NamespacedKey(plugin, "reforge_id");
    }

    public void reload() {
        definitions.clear();
        ConfigurationSection section = configService.reforges().getConfigurationSection("reforges");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection reforgeSection = section.getConfigurationSection(id);
            if (reforgeSection == null) {
                continue;
            }
            String normalizedId = id.toUpperCase(Locale.ROOT);
            definitions.put(normalizedId, new ReforgeDefinition(
                    normalizedId,
                    reforgeSection.getString("display-name", normalizedId),
                    reforgeSection.getString("prefix", normalizedId),
                    categories(reforgeSection.getStringList("allowed-categories")),
                    Math.max(0.0D, reforgeSection.getDouble("cost-multiplier", 1.0D)),
                    reforgeSection.getString("required-item", "").toUpperCase(Locale.ROOT),
                    Math.max(1, reforgeSection.getInt("required-amount", 1)),
                    statsByRarity(reforgeSection.getConfigurationSection("stats"))
            ));
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.reforges", true);
    }

    public Optional<ReforgeDefinition> definition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<ReforgeDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(ReforgeDefinition::id))
                .toList();
    }

    public List<ReforgeDefinition> applicableDefinitions(CustomItemDefinition itemDefinition) {
        if (itemDefinition == null) {
            return List.of();
        }
        return definitions().stream()
                .filter(reforge -> isApplicable(reforge, itemDefinition))
                .toList();
    }

    public Optional<ReforgeDefinition> definition(ItemStack itemStack) {
        if (!enabled()) {
            return Optional.empty();
        }
        return reforgeId(itemStack).flatMap(this::definition);
    }

    public Optional<String> reforgeId(ItemStack itemStack) {
        if (itemStack == null || !itemStack.hasItemMeta()) {
            return Optional.empty();
        }
        String reforgeId = itemStack.getItemMeta().getPersistentDataContainer().get(reforgeKey, PersistentDataType.STRING);
        if (reforgeId == null || reforgeId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(reforgeId.toUpperCase(Locale.ROOT));
    }

    public Map<String, Double> stats(ItemStack itemStack, CustomItemDefinition itemDefinition) {
        ReforgeDefinition reforge = definition(itemStack).orElse(null);
        if (reforge == null || itemDefinition == null || !isApplicable(reforge, itemDefinition)) {
            return Map.of();
        }
        return stats(reforge, itemDefinition.rarity());
    }

    public Map<String, Double> stats(ReforgeDefinition reforge, Rarity rarity) {
        Map<String, Double> stats = reforge.statsByRarity().get(rarity);
        if (stats != null) {
            return stats;
        }
        return reforge.statsByRarity().getOrDefault(Rarity.COMMON, Map.of());
    }

    public boolean applyHeld(Player player, String rawReforgeId) {
        if (!enabled()) {
            text.send(player, "commands.reforge-disabled");
            return false;
        }
        ReforgeDefinition reforge = definition(rawReforgeId).orElse(null);
        if (reforge == null) {
            text.send(player, "errors.unknown-reforge", List.of(TextService.raw("reforge", rawReforgeId)));
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition itemDefinition = customItems.definition(held).orElse(null);
        if (itemDefinition == null) {
            text.send(player, "commands.reforge-held-missing");
            return false;
        }
        if (!isApplicable(reforge, itemDefinition)) {
            text.send(player, "commands.reforge-not-applicable", placeholders(reforge, itemDefinition, cost(reforge, itemDefinition)));
            return false;
        }
        if (reforgeId(held).filter(existing -> existing.equals(reforge.id())).isPresent()) {
            text.send(player, "commands.reforge-already", placeholders(reforge, itemDefinition, cost(reforge, itemDefinition)));
            return false;
        }
        double cost = cost(reforge, itemDefinition);
        if (!reforge.requiredItemId().isBlank() && !hasRequiredItem(player, reforge)) {
            text.send(player, "commands.reforge-stone-missing", placeholders(reforge, itemDefinition, cost));
            return false;
        }
        if (!economy.spendPurse(player, cost)) {
            text.send(player, "commands.reforge-no-money", placeholders(reforge, itemDefinition, cost));
            return false;
        }
        if (!reforge.requiredItemId().isBlank()) {
            consumeRequiredItem(player, reforge);
        }
        ItemMeta meta = held.getItemMeta();
        meta.getPersistentDataContainer().set(reforgeKey, PersistentDataType.STRING, reforge.id());
        held.setItemMeta(meta);
        customItems.refreshItem(held);
        text.send(player, "commands.reforge-applied", placeholders(reforge, itemDefinition, cost));
        return true;
    }

    public boolean removeHeld(Player player) {
        if (!enabled()) {
            text.send(player, "commands.reforge-disabled");
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition itemDefinition = customItems.definition(held).orElse(null);
        if (itemDefinition == null) {
            text.send(player, "commands.reforge-held-missing");
            return false;
        }
        ReforgeDefinition reforge = definition(held).orElse(null);
        if (reforge == null) {
            text.send(player, "commands.reforge-none");
            return false;
        }
        double cost = removeCost();
        if (!economy.spendPurse(player, cost)) {
            text.send(player, "commands.reforge-no-money", placeholders(reforge, itemDefinition, cost));
            return false;
        }
        ItemMeta meta = held.getItemMeta();
        meta.getPersistentDataContainer().remove(reforgeKey);
        held.setItemMeta(meta);
        customItems.refreshItem(held);
        text.send(player, "commands.reforge-removed", placeholders(reforge, itemDefinition, cost));
        return true;
    }

    public void sendList(CommandSender sender) {
        if (!enabled()) {
            text.send(sender, "commands.reforge-disabled");
            return;
        }
        text.send(sender, "commands.reforge-list-header");
        for (ReforgeDefinition definition : definitions()) {
            text.send(sender, "commands.reforge-list-line", List.of(
                    TextService.raw("id", definition.id()),
                    TextService.parsed("reforge", definition.displayName()),
                    TextService.raw("categories", String.join(", ", definition.allowedCategories())),
                    TextService.parsed("required_item", requiredItemName(definition)),
                    TextService.raw("required_amount", Integer.toString(definition.requiredItemId().isBlank() ? 0 : definition.requiredAmount())),
                    TextService.parsed("required", requiredItemLine(definition))
            ));
        }
    }

    public boolean isApplicable(ReforgeDefinition reforge, CustomItemDefinition itemDefinition) {
        if (reforge.allowedCategories().isEmpty() || reforge.allowedCategories().contains("ALL")) {
            return true;
        }
        return reforge.allowedCategories().contains(itemDefinition.category().toUpperCase(Locale.ROOT));
    }

    public double cost(ReforgeDefinition reforge, CustomItemDefinition itemDefinition) {
        double base = Math.max(0.0D, configService.reforges().getDouble("settings.base-costs." + itemDefinition.rarity().name(), 0.0D));
        return base * Math.max(0.0D, reforge.costMultiplier());
    }

    public double removeCost() {
        return Math.max(0.0D, configService.reforges().getDouble("settings.remove-cost", 0.0D));
    }

    public List<TextService.TextPlaceholder> placeholders(ReforgeDefinition reforge, CustomItemDefinition itemDefinition, double cost) {
        return List.of(
                TextService.raw("reforge_id", reforge.id()),
                TextService.parsed("reforge", reforge.displayName()),
                TextService.parsed("reforge_prefix", reforge.prefix()),
                TextService.parsed("item", itemDefinition.displayName()),
                TextService.raw("category", itemDefinition.category().toUpperCase(Locale.ROOT)),
                TextService.parsed("required_item", requiredItemName(reforge)),
                TextService.raw("required_amount", Integer.toString(reforge.requiredItemId().isBlank() ? 0 : reforge.requiredAmount())),
                TextService.raw("cost", text.formatNumber(cost))
        );
    }

    public String requiredItemName(ReforgeDefinition reforge) {
        if (reforge.requiredItemId().isBlank()) {
            return text.rawMessage("reforges.no-required-item");
        }
        return customItems.definition(reforge.requiredItemId())
                .map(CustomItemDefinition::displayName)
                .orElse(reforge.requiredItemId());
    }

    public String requiredItemLine(ReforgeDefinition reforge) {
        if (reforge.requiredItemId().isBlank()) {
            return text.rawMessage("reforges.no-required-item");
        }
        return "<yellow>" + reforge.requiredAmount() + "x</yellow> " + requiredItemName(reforge);
    }

    public boolean hasRequiredItem(Player player, ReforgeDefinition reforge) {
        return countRequiredItems(player, reforge) >= reforge.requiredAmount();
    }

    private int countRequiredItems(Player player, ReforgeDefinition reforge) {
        String itemId = reforge.requiredItemId();
        if (itemId.isBlank()) {
            return reforge.requiredAmount();
        }
        int heldSlot = player.getInventory().getHeldItemSlot();
        int amount = 0;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (slot == heldSlot) {
                continue;
            }
            ItemStack itemStack = player.getInventory().getItem(slot);
            if (customItems.definition(itemStack).map(CustomItemDefinition::id).filter(itemId::equals).isPresent()) {
                amount += itemStack.getAmount();
            }
        }
        return amount;
    }

    private void consumeRequiredItem(Player player, ReforgeDefinition reforge) {
        String itemId = reforge.requiredItemId();
        int remaining = reforge.requiredAmount();
        int heldSlot = player.getInventory().getHeldItemSlot();
        for (int slot = 0; slot < player.getInventory().getSize() && remaining > 0; slot++) {
            if (slot == heldSlot) {
                continue;
            }
            ItemStack itemStack = player.getInventory().getItem(slot);
            if (customItems.definition(itemStack).map(CustomItemDefinition::id).filter(itemId::equals).isEmpty()) {
                continue;
            }
            int remove = Math.min(remaining, itemStack.getAmount());
            remaining -= remove;
            if (itemStack.getAmount() <= remove) {
                player.getInventory().setItem(slot, null);
            } else {
                itemStack.setAmount(itemStack.getAmount() - remove);
            }
        }
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

    private Map<Rarity, Map<String, Double>> statsByRarity(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<Rarity, Map<String, Double>> stats = new EnumMap<>(Rarity.class);
        for (String rarityKey : section.getKeys(false)) {
            ConfigurationSection raritySection = section.getConfigurationSection(rarityKey);
            if (raritySection == null) {
                continue;
            }
            Map<String, Double> rarityStats = new HashMap<>();
            for (String stat : raritySection.getKeys(false)) {
                rarityStats.put(StatSnapshot.normalize(stat), raritySection.getDouble(stat, 0.0D));
            }
            stats.put(Rarity.parse(rarityKey), rarityStats);
        }
        return stats;
    }
}
