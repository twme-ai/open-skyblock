package io.github.openskyblock.stats;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class StatService {
    private final ConfigService configService;
    private final TextService text;
    private final CustomItemService customItems;

    public StatService(ConfigService configService, TextService text, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.customItems = customItems;
    }

    public StatSnapshot snapshot(Player player) {
        Map<String, Double> stats = baseStats();
        addEquipmentStats(stats, player);
        addAccessoryStats(stats, player);
        return new StatSnapshot(stats);
    }

    public void sendStats(Player player) {
        StatSnapshot snapshot = snapshot(player);
        text.send(player, "commands.stats-header");
        for (String stat : displayOrder()) {
            text.send(player, "commands.stats-line", List.of(
                    TextService.raw("stat", statLabel(stat)),
                    TextService.raw("value", text.formatNumber(snapshot.stat(stat)))
            ));
        }
    }

    public List<String> displayOrder() {
        List<String> configured = configService.main().getStringList("stats.display-order");
        if (!configured.isEmpty()) {
            return configured.stream().map(StatSnapshot::normalize).toList();
        }
        return List.of("health", "defense", "damage", "strength", "crit_chance", "crit_damage", "intelligence", "speed", "ferocity");
    }

    public String statLabel(String stat) {
        String normalized = StatSnapshot.normalize(stat);
        String configured = configService.messages().getString("items.stat-labels." + normalized);
        if (configured != null && !configured.isBlank()) {
            return configured;
        }
        String readable = normalized.replace('_', ' ');
        if (readable.isBlank()) {
            return stat;
        }
        return Character.toUpperCase(readable.charAt(0)) + readable.substring(1);
    }

    private Map<String, Double> baseStats() {
        Map<String, Double> stats = new LinkedHashMap<>();
        for (String key : displayOrder()) {
            stats.put(key, configService.main().getDouble("stats.base." + key, 0.0D));
        }
        return stats;
    }

    private void addEquipmentStats(Map<String, Double> stats, Player player) {
        addItemStats(stats, player.getInventory().getItemInMainHand(), false);
        addItemStats(stats, player.getInventory().getItemInOffHand(), false);
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            addItemStats(stats, armor, false);
        }
    }

    private void addAccessoryStats(Map<String, Double> stats, Player player) {
        for (ItemStack itemStack : player.getInventory().getStorageContents()) {
            addItemStats(stats, itemStack, true);
        }
    }

    private void addItemStats(Map<String, Double> stats, ItemStack itemStack, boolean accessoryOnly) {
        CustomItemDefinition definition = customItems.definition(itemStack).orElse(null);
        if (definition == null) {
            return;
        }
        boolean isAccessory = definition.category().equalsIgnoreCase("ACCESSORY");
        if (accessoryOnly != isAccessory) {
            return;
        }
        for (Map.Entry<String, Double> entry : definition.stats().entrySet()) {
            String stat = StatSnapshot.normalize(entry.getKey());
            stats.put(stat, stats.getOrDefault(stat, 0.0D) + entry.getValue());
        }
    }
}
