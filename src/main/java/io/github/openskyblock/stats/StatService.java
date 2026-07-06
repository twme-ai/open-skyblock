package io.github.openskyblock.stats;

import io.github.openskyblock.accessory.AccessoryService;
import io.github.openskyblock.accessory.TuningService;
import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.enchant.EnchantmentService;
import io.github.openskyblock.equipment.EquipmentService;
import io.github.openskyblock.gemstone.GemstoneService;
import io.github.openskyblock.pet.PetService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.reforge.ReforgeService;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.star.StarService;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class StatService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CustomItemService customItems;
    private final AccessoryService accessories;
    private final TuningService tuning;
    private final EquipmentService equipment;
    private final ArmorSetService armorSets;
    private final PetService pets;
    private final ReforgeService reforges;
    private final EnchantmentService enchantments;
    private final StarService stars;
    private final GemstoneService gemstones;

    public StatService(ConfigService configService, TextService text, ProfileManager profiles, CustomItemService customItems, AccessoryService accessories, TuningService tuning, EquipmentService equipment, ArmorSetService armorSets, PetService pets, ReforgeService reforges, EnchantmentService enchantments, StarService stars, GemstoneService gemstones) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.customItems = customItems;
        this.accessories = accessories;
        this.tuning = tuning;
        this.equipment = equipment;
        this.armorSets = armorSets;
        this.pets = pets;
        this.reforges = reforges;
        this.enchantments = enchantments;
        this.stars = stars;
        this.gemstones = gemstones;
    }

    public StatSnapshot snapshot(Player player) {
        Map<String, Double> stats = baseStats();
        SkyBlockProfile profile = profiles.profile(player);
        addEquipmentStats(stats, player);
        addArmorSetStats(stats, player);
        addProfileEquipmentStats(stats, profile);
        addAccessoryStats(stats, profile);
        addTuningStats(stats, profile);
        addPetStats(stats, profile);
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

    private void addArmorSetStats(Map<String, Double> stats, Player player) {
        Map<String, Integer> wornPieces = new LinkedHashMap<>();
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            CustomItemDefinition definition = customItems.definition(armor).orElse(null);
            if (definition == null || definition.armorSet() == null || definition.armorSet().isBlank()) {
                continue;
            }
            String armorSet = definition.armorSet().toUpperCase();
            wornPieces.put(armorSet, wornPieces.getOrDefault(armorSet, 0) + 1);
        }
        for (Map.Entry<String, Integer> entry : wornPieces.entrySet()) {
            ArmorSetDefinition definition = armorSets.definition(entry.getKey()).orElse(null);
            if (definition == null || entry.getValue() < definition.requiredPieces()) {
                continue;
            }
            for (Map.Entry<String, Double> stat : definition.stats().entrySet()) {
                stats.put(stat.getKey(), stats.getOrDefault(stat.getKey(), 0.0D) + stat.getValue());
            }
        }
    }

    private void addProfileEquipmentStats(Map<String, Double> stats, SkyBlockProfile profile) {
        if (!equipment.enabled()) {
            return;
        }
        for (Map.Entry<String, ItemStack> entry : profile.equipment().entrySet()) {
            if (equipment.slot(entry.getKey()).isEmpty()) {
                continue;
            }
            ItemStack itemStack = entry.getValue();
            CustomItemDefinition definition = customItems.definition(itemStack).orElse(null);
            if (definition == null || !equipment.isEquipment(definition)) {
                continue;
            }
            addItemStats(stats, itemStack, false);
        }
    }

    private void addAccessoryStats(Map<String, Double> stats, SkyBlockProfile profile) {
        for (CustomItemDefinition definition : accessories.accessories(profile)) {
            for (Map.Entry<String, Double> entry : definition.stats().entrySet()) {
                String stat = StatSnapshot.normalize(entry.getKey());
                stats.put(stat, stats.getOrDefault(stat, 0.0D) + entry.getValue());
            }
        }
        stats.put("magical_power", stats.getOrDefault("magical_power", 0.0D) + accessories.magicalPower(profile));
    }

    private void addTuningStats(Map<String, Double> stats, SkyBlockProfile profile) {
        for (Map.Entry<String, Double> entry : tuning.tuningBonuses(profile).entrySet()) {
            stats.put(entry.getKey(), stats.getOrDefault(entry.getKey(), 0.0D) + entry.getValue());
        }
    }

    private void addPetStats(Map<String, Double> stats, SkyBlockProfile profile) {
        for (Map.Entry<String, Double> entry : pets.activeStats(profile).entrySet()) {
            stats.put(entry.getKey(), stats.getOrDefault(entry.getKey(), 0.0D) + entry.getValue());
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
        Map<String, Double> itemStats = new HashMap<>();
        for (Map.Entry<String, Double> entry : definition.stats().entrySet()) {
            String stat = StatSnapshot.normalize(entry.getKey());
            itemStats.put(stat, itemStats.getOrDefault(stat, 0.0D) + entry.getValue());
        }
        for (Map.Entry<String, Double> entry : reforges.stats(itemStack, definition).entrySet()) {
            String stat = StatSnapshot.normalize(entry.getKey());
            itemStats.put(stat, itemStats.getOrDefault(stat, 0.0D) + entry.getValue());
        }
        for (Map.Entry<String, Double> entry : enchantments.stats(itemStack, definition).entrySet()) {
            String stat = StatSnapshot.normalize(entry.getKey());
            itemStats.put(stat, itemStats.getOrDefault(stat, 0.0D) + entry.getValue());
        }
        for (Map.Entry<String, Double> entry : gemstones.stats(itemStack, definition).entrySet()) {
            String stat = StatSnapshot.normalize(entry.getKey());
            itemStats.put(stat, itemStats.getOrDefault(stat, 0.0D) + entry.getValue());
        }
        for (Map.Entry<String, Double> entry : stars.bonusStats(itemStack, itemStats).entrySet()) {
            String stat = StatSnapshot.normalize(entry.getKey());
            itemStats.put(stat, itemStats.getOrDefault(stat, 0.0D) + entry.getValue());
        }
        for (Map.Entry<String, Double> entry : itemStats.entrySet()) {
            String stat = StatSnapshot.normalize(entry.getKey());
            stats.put(stat, stats.getOrDefault(stat, 0.0D) + entry.getValue());
        }
    }
}
