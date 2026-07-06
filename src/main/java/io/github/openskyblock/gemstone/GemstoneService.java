package io.github.openskyblock.gemstone;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.stats.StatSnapshot;
import java.util.ArrayList;
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

public final class GemstoneService {
    private final ConfigService configService;
    private final TextService text;
    private final EconomyService economy;
    private final CustomItemService customItems;
    private final NamespacedKey gemstonesKey;
    private final Map<String, GemstoneDefinition> gemstones = new HashMap<>();
    private final Map<String, GemstoneSlotDefinition> slots = new HashMap<>();

    public GemstoneService(JavaPlugin plugin, ConfigService configService, TextService text, EconomyService economy, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.economy = economy;
        this.customItems = customItems;
        this.gemstonesKey = new NamespacedKey(plugin, "gemstones");
    }

    public void reload() {
        gemstones.clear();
        slots.clear();
        ConfigurationSection slotSection = configService.gemstones().getConfigurationSection("slots");
        if (slotSection != null) {
            for (String id : slotSection.getKeys(false)) {
                ConfigurationSection section = slotSection.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                String normalizedId = id.toUpperCase(Locale.ROOT);
                slots.put(normalizedId, new GemstoneSlotDefinition(
                        normalizedId,
                        section.getString("display-name", normalizedId),
                        section.getString("symbol", normalizedId.substring(0, 1)),
                        normalizedSet(section.getStringList("allowed-gemstones"))
                ));
            }
        }
        ConfigurationSection gemstoneSection = configService.gemstones().getConfigurationSection("gemstones");
        if (gemstoneSection != null) {
            for (String id : gemstoneSection.getKeys(false)) {
                ConfigurationSection section = gemstoneSection.getConfigurationSection(id);
                if (section == null) {
                    continue;
                }
                String normalizedId = id.toUpperCase(Locale.ROOT);
                gemstones.put(normalizedId, new GemstoneDefinition(
                        normalizedId,
                        section.getString("display-name", normalizedId),
                        statsByTier(section.getConfigurationSection("tiers"))
                ));
            }
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.gemstones", true);
    }

    public Optional<GemstoneDefinition> gemstone(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(gemstones.get(id.toUpperCase(Locale.ROOT)));
    }

    public Optional<GemstoneSlotDefinition> slot(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(slots.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<GemstoneDefinition> gemstones() {
        return gemstones.values().stream()
                .sorted(Comparator.comparing(GemstoneDefinition::id))
                .toList();
    }

    public List<GemstoneSlotDefinition> slots() {
        return slots.values().stream()
                .sorted(Comparator.comparing(GemstoneSlotDefinition::id))
                .toList();
    }

    public List<String> tiers(String gemstoneId) {
        GemstoneDefinition gemstone = gemstone(gemstoneId).orElse(null);
        if (gemstone == null) {
            return configuredTierOrder();
        }
        List<String> ordered = new ArrayList<>();
        for (String tier : configuredTierOrder()) {
            if (gemstone.statsByTier().containsKey(tier)) {
                ordered.add(tier);
            }
        }
        for (String tier : gemstone.statsByTier().keySet().stream().sorted().toList()) {
            if (!ordered.contains(tier)) {
                ordered.add(tier);
            }
        }
        return ordered;
    }

    public List<GemstoneSlotDefinition> availableSlots(CustomItemDefinition definition) {
        List<String> rawSlots = configService.gemstones().getStringList("item-slots." + definition.id());
        if (rawSlots.isEmpty()) {
            rawSlots = configService.gemstones().getStringList("category-slots." + definition.category().toUpperCase(Locale.ROOT));
        }
        List<GemstoneSlotDefinition> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String rawSlot : rawSlots) {
            String slotId = rawSlot.toUpperCase(Locale.ROOT);
            if (!seen.add(slotId)) {
                continue;
            }
            slot(slotId).ifPresent(result::add);
        }
        return result;
    }

    public Map<String, AppliedGemstone> applied(ItemStack itemStack) {
        if (!enabled() || itemStack == null || !itemStack.hasItemMeta()) {
            return Map.of();
        }
        String raw = itemStack.getItemMeta().getPersistentDataContainer().get(gemstonesKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return Map.of();
        }
        Map<String, AppliedGemstone> applied = new LinkedHashMap<>();
        for (String part : raw.split(";")) {
            String[] slotAndGemstone = part.split("=", 2);
            if (slotAndGemstone.length != 2) {
                continue;
            }
            String[] gemstoneAndTier = slotAndGemstone[1].split(":", 2);
            if (gemstoneAndTier.length != 2) {
                continue;
            }
            String slotId = slotAndGemstone[0].toUpperCase(Locale.ROOT);
            String gemstoneId = gemstoneAndTier[0].toUpperCase(Locale.ROOT);
            String tier = gemstoneAndTier[1].toUpperCase(Locale.ROOT);
            if (slot(slotId).isPresent() && gemstone(gemstoneId).map(gemstone -> gemstone.statsByTier().containsKey(tier)).orElse(false)) {
                applied.put(slotId, new AppliedGemstone(gemstoneId, tier));
            }
        }
        return applied;
    }

    public Map<String, Double> stats(ItemStack itemStack, CustomItemDefinition definition) {
        if (!enabled() || definition == null) {
            return Map.of();
        }
        Set<String> availableSlotIds = availableSlots(definition).stream()
                .map(GemstoneSlotDefinition::id)
                .collect(java.util.stream.Collectors.toSet());
        Map<String, Double> stats = new HashMap<>();
        for (Map.Entry<String, AppliedGemstone> entry : applied(itemStack).entrySet()) {
            if (!availableSlotIds.contains(entry.getKey())) {
                continue;
            }
            AppliedGemstone appliedGemstone = entry.getValue();
            GemstoneDefinition gemstone = gemstone(appliedGemstone.gemstoneId()).orElse(null);
            if (gemstone == null) {
                continue;
            }
            Map<String, Double> gemstoneStats = gemstone.statsByTier().getOrDefault(appliedGemstone.tier(), Map.of());
            for (Map.Entry<String, Double> stat : gemstoneStats.entrySet()) {
                stats.put(stat.getKey(), stats.getOrDefault(stat.getKey(), 0.0D) + stat.getValue());
            }
        }
        return stats;
    }

    public boolean applyHeld(Player player, String rawSlotId, String rawGemstoneId, String rawTier) {
        if (!enabled()) {
            text.send(player, "commands.gemstone-disabled");
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition itemDefinition = customItems.definition(held).orElse(null);
        if (itemDefinition == null) {
            text.send(player, "commands.gemstone-held-missing");
            return false;
        }
        GemstoneSlotDefinition slot = availableSlot(itemDefinition, rawSlotId).orElse(null);
        if (slot == null) {
            text.send(player, "commands.gemstone-unknown-slot", List.of(TextService.raw("slot", rawSlotId)));
            return false;
        }
        GemstoneDefinition gemstone = gemstone(rawGemstoneId).orElse(null);
        if (gemstone == null) {
            text.send(player, "errors.unknown-gemstone", List.of(TextService.raw("gemstone", rawGemstoneId)));
            return false;
        }
        String tier = rawTier.toUpperCase(Locale.ROOT);
        if (!gemstone.statsByTier().containsKey(tier)) {
            text.send(player, "commands.gemstone-unknown-tier", List.of(TextService.raw("tier", rawTier)));
            return false;
        }
        if (!slot.allowedGemstones().isEmpty() && !slot.allowedGemstones().contains(gemstone.id())) {
            text.send(player, "commands.gemstone-not-applicable", placeholders(itemDefinition, slot, gemstone, tier, 0.0D));
            return false;
        }
        Map<String, AppliedGemstone> current = new LinkedHashMap<>(applied(held));
        AppliedGemstone existing = current.get(slot.id());
        if (existing != null && existing.gemstoneId().equals(gemstone.id()) && existing.tier().equals(tier)) {
            text.send(player, "commands.gemstone-already", placeholders(itemDefinition, slot, gemstone, tier, 0.0D));
            return false;
        }
        double cost = applyCost(tier);
        if (!economy.spendPurse(player, cost)) {
            text.send(player, "commands.gemstone-no-money", placeholders(itemDefinition, slot, gemstone, tier, cost));
            return false;
        }
        current.put(slot.id(), new AppliedGemstone(gemstone.id(), tier));
        writeGemstones(held, current);
        customItems.refreshItem(held);
        text.send(player, "commands.gemstone-applied", placeholders(itemDefinition, slot, gemstone, tier, cost));
        return true;
    }

    public boolean removeHeld(Player player, String rawSlotId) {
        if (!enabled()) {
            text.send(player, "commands.gemstone-disabled");
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition itemDefinition = customItems.definition(held).orElse(null);
        if (itemDefinition == null) {
            text.send(player, "commands.gemstone-held-missing");
            return false;
        }
        GemstoneSlotDefinition slot = availableSlot(itemDefinition, rawSlotId).orElse(null);
        if (slot == null) {
            text.send(player, "commands.gemstone-unknown-slot", List.of(TextService.raw("slot", rawSlotId)));
            return false;
        }
        Map<String, AppliedGemstone> current = new LinkedHashMap<>(applied(held));
        AppliedGemstone removed = current.remove(slot.id());
        if (removed == null) {
            text.send(player, "commands.gemstone-empty", List.of(TextService.parsed("slot", slot.displayName())));
            return false;
        }
        GemstoneDefinition gemstone = gemstone(removed.gemstoneId()).orElse(null);
        if (gemstone == null) {
            text.send(player, "errors.unknown-gemstone", List.of(TextService.raw("gemstone", removed.gemstoneId())));
            return false;
        }
        double cost = removeCost();
        if (!economy.spendPurse(player, cost)) {
            text.send(player, "commands.gemstone-no-money", placeholders(itemDefinition, slot, gemstone, removed.tier(), cost));
            return false;
        }
        writeGemstones(held, current);
        customItems.refreshItem(held);
        text.send(player, "commands.gemstone-removed", placeholders(itemDefinition, slot, gemstone, removed.tier(), cost));
        return true;
    }

    public void sendInfo(CommandSender sender) {
        if (!enabled()) {
            text.send(sender, "commands.gemstone-disabled");
            return;
        }
        text.send(sender, "commands.gemstone-list-header");
        for (GemstoneDefinition gemstone : gemstones()) {
            text.send(sender, "commands.gemstone-list-line", List.of(
                    TextService.raw("id", gemstone.id()),
                    TextService.parsed("gemstone", gemstone.displayName()),
                    TextService.raw("tiers", String.join(", ", tiers(gemstone.id())))
            ));
        }
    }

    public void sendHeldSlots(Player player) {
        if (!enabled()) {
            text.send(player, "commands.gemstone-disabled");
            return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        CustomItemDefinition itemDefinition = customItems.definition(held).orElse(null);
        if (itemDefinition == null) {
            text.send(player, "commands.gemstone-held-missing");
            return;
        }
        List<GemstoneSlotDefinition> availableSlots = availableSlots(itemDefinition);
        if (availableSlots.isEmpty()) {
            text.send(player, "commands.gemstone-no-slots", List.of(TextService.parsed("item", itemDefinition.displayName())));
            return;
        }
        Map<String, AppliedGemstone> applied = applied(held);
        text.send(player, "commands.gemstone-slots-header", List.of(TextService.parsed("item", itemDefinition.displayName())));
        for (GemstoneSlotDefinition slot : availableSlots) {
            AppliedGemstone appliedGemstone = applied.get(slot.id());
            String value = text.rawMessage("gemstones.empty-slot");
            if (appliedGemstone != null) {
                GemstoneDefinition gemstone = gemstone(appliedGemstone.gemstoneId()).orElse(null);
                if (gemstone != null) {
                    value = gemstone.displayName() + " <gray>" + appliedGemstone.tier() + "</gray>";
                }
            }
            text.send(player, "commands.gemstone-slots-line", List.of(
                    TextService.raw("slot_id", slot.id()),
                    TextService.parsed("slot", slot.displayName()),
                    TextService.parsed("gemstone", value)
            ));
        }
    }

    public List<Component> lore(ItemStack itemStack, CustomItemDefinition definition) {
        if (!enabled()) {
            return List.of();
        }
        List<GemstoneSlotDefinition> availableSlots = availableSlots(definition);
        if (availableSlots.isEmpty()) {
            return List.of();
        }
        Map<String, AppliedGemstone> applied = applied(itemStack);
        List<Component> lore = new ArrayList<>();
        for (GemstoneSlotDefinition slot : availableSlots) {
            AppliedGemstone appliedGemstone = applied.get(slot.id());
            if (appliedGemstone == null) {
                lore.add(text.message("items.gemstone-empty-line", List.of(
                        TextService.parsed("slot", slot.displayName()),
                        TextService.raw("slot_symbol", slot.symbol())
                )));
                continue;
            }
            GemstoneDefinition gemstone = gemstone(appliedGemstone.gemstoneId()).orElse(null);
            if (gemstone == null) {
                continue;
            }
            lore.add(text.message("items.gemstone-line", List.of(
                    TextService.parsed("slot", slot.displayName()),
                    TextService.raw("slot_symbol", slot.symbol()),
                    TextService.parsed("gemstone", gemstone.displayName()),
                    TextService.raw("tier", appliedGemstone.tier())
            )));
        }
        return lore;
    }

    private Optional<GemstoneSlotDefinition> availableSlot(CustomItemDefinition definition, String slotId) {
        String normalized = slotId == null ? "" : slotId.toUpperCase(Locale.ROOT);
        return availableSlots(definition).stream()
                .filter(slot -> slot.id().equals(normalized))
                .findFirst();
    }

    private void writeGemstones(ItemStack itemStack, Map<String, AppliedGemstone> gemstones) {
        ItemMeta meta = itemStack.getItemMeta();
        if (gemstones.isEmpty()) {
            meta.getPersistentDataContainer().remove(gemstonesKey);
        } else {
            String raw = gemstones.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(entry -> entry.getKey() + "=" + entry.getValue().gemstoneId() + ":" + entry.getValue().tier())
                    .collect(java.util.stream.Collectors.joining(";"));
            meta.getPersistentDataContainer().set(gemstonesKey, PersistentDataType.STRING, raw);
        }
        itemStack.setItemMeta(meta);
    }

    private double applyCost(String tier) {
        return Math.max(0.0D, configService.gemstones().getDouble("settings.apply-costs." + tier.toUpperCase(Locale.ROOT), 0.0D));
    }

    private double removeCost() {
        return Math.max(0.0D, configService.gemstones().getDouble("settings.remove-cost", 0.0D));
    }

    private List<TextService.TextPlaceholder> placeholders(CustomItemDefinition itemDefinition, GemstoneSlotDefinition slot, GemstoneDefinition gemstone, String tier, double cost) {
        return List.of(
                TextService.parsed("item", itemDefinition.displayName()),
                TextService.raw("slot_id", slot.id()),
                TextService.parsed("slot", slot.displayName()),
                TextService.raw("slot_symbol", slot.symbol()),
                TextService.raw("gemstone_id", gemstone.id()),
                TextService.parsed("gemstone", gemstone.displayName()),
                TextService.raw("tier", tier),
                TextService.raw("cost", text.formatNumber(cost))
        );
    }

    private List<String> configuredTierOrder() {
        List<String> configured = configService.gemstones().getStringList("settings.tier-order");
        if (!configured.isEmpty()) {
            return configured.stream().map(value -> value.toUpperCase(Locale.ROOT)).toList();
        }
        return List.of("ROUGH", "FLAWED", "FINE", "FLAWLESS", "PERFECT");
    }

    private Set<String> normalizedSet(List<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.toUpperCase(Locale.ROOT));
            }
        }
        return normalized;
    }

    private Map<String, Map<String, Double>> statsByTier(ConfigurationSection section) {
        if (section == null) {
            return Map.of();
        }
        Map<String, Map<String, Double>> statsByTier = new HashMap<>();
        for (String tier : section.getKeys(false)) {
            ConfigurationSection tierSection = section.getConfigurationSection(tier);
            if (tierSection == null) {
                continue;
            }
            Map<String, Double> stats = new HashMap<>();
            for (String stat : tierSection.getKeys(false)) {
                stats.put(StatSnapshot.normalize(stat), tierSection.getDouble(stat, 0.0D));
            }
            statsByTier.put(tier.toUpperCase(Locale.ROOT), stats);
        }
        return statsByTier;
    }

    public record AppliedGemstone(String gemstoneId, String tier) {
    }
}
