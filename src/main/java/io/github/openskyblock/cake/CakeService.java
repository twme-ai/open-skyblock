package io.github.openskyblock.cake;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.PlacedCake;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import io.github.openskyblock.stats.StatSnapshot;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class CakeService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CustomItemService customItems;
    private final Map<String, CakeDefinition> definitions = new LinkedHashMap<>();
    private boolean allowVisitors = true;

    public CakeService(ConfigService configService, TextService text, ProfileManager profiles, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.customItems = customItems;
    }

    public void reload() {
        definitions.clear();
        this.allowVisitors = configService.cakes().getBoolean("settings.allow-visitor-activation", true);
        ConfigurationSection section = configService.cakes().getConfigurationSection("cakes");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection cakeSection = section.getConfigurationSection(id);
            if (cakeSection == null) {
                continue;
            }
            Material material = Material.matchMaterial(cakeSection.getString("material", "CAKE"));
            String normalizedId = id.toUpperCase(Locale.ROOT);
            definitions.put(normalizedId, new CakeDefinition(
                    normalizedId,
                    cakeSection.getString("display-name", id),
                    cakeSection.getString("item-id", normalizedId).toUpperCase(Locale.ROOT),
                    material == null ? Material.CAKE : material,
                    Math.max(1L, cakeSection.getLong("duration-seconds", 172_800L)),
                    stats(cakeSection.getConfigurationSection("stats"))
            ));
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.cakes", true);
    }

    public List<CakeDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(CakeDefinition::id))
                .toList();
    }

    public Optional<CakeDefinition> definition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toUpperCase(Locale.ROOT)));
    }

    public Optional<CakeDefinition> definition(ItemStack itemStack) {
        CustomItemDefinition item = customItems.definition(itemStack).orElse(null);
        if (item == null) {
            return Optional.empty();
        }
        return definitions.values().stream()
                .filter(cake -> cake.itemId().equalsIgnoreCase(item.id()))
                .findFirst();
    }

    public boolean place(Player player, CakeDefinition definition, Location location) {
        if (!enabled()) {
            text.send(player, "commands.cake-disabled");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.placedCakes().size() >= maxPlaced()) {
            text.send(player, "commands.cake-limit", List.of(TextService.raw("limit", Integer.toString(maxPlaced()))));
            return false;
        }
        if (find(location).isPresent()) {
            text.send(player, "commands.cake-location-used");
            return false;
        }
        PlacedCake placedCake = new PlacedCake(definition.id(), null, 0, 0, 0);
        placedCake.location(location);
        profile.placedCakes().add(placedCake);
        location.getBlock().setType(definition.material(), false);
        text.send(player, "commands.cake-placed", List.of(TextService.parsed("cake", definition.displayName())));
        return true;
    }

    public Optional<CakePlacement> find(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        for (SkyBlockProfile profile : profiles.loadedProfiles()) {
            for (int slot = 0; slot < profile.placedCakes().size(); slot++) {
                PlacedCake placedCake = profile.placedCakes().get(slot);
                if (!placedCake.matches(location)) {
                    continue;
                }
                CakeDefinition definition = definition(placedCake.id()).orElse(null);
                if (definition == null) {
                    continue;
                }
                return Optional.of(new CakePlacement(profile, slot, placedCake, definition));
            }
        }
        return Optional.empty();
    }

    public boolean canActivate(Player player, CakePlacement placement) {
        if (allowVisitors) {
            return true;
        }
        return placement.profile().uniqueId().equals(player.getUniqueId()) || player.hasPermission("openskyblock.admin");
    }

    public void activate(Player player, CakePlacement placement) {
        if (!enabled()) {
            text.send(player, "commands.cake-disabled");
            return;
        }
        if (!canActivate(player, placement)) {
            text.send(player, "commands.island-protected");
            return;
        }
        CakeDefinition definition = placement.definition();
        SkyBlockProfile profile = profiles.profile(player);
        long expiresAt = System.currentTimeMillis() + definition.durationSeconds() * 1000L;
        profile.cakeBuffs().put(definition.id(), expiresAt);
        text.send(player, "commands.cake-activated", List.of(
                TextService.parsed("cake", definition.displayName()),
                TextService.raw("duration", formatDuration(definition.durationSeconds()))
        ));
    }

    public boolean pickup(Player player, CakePlacement placement) {
        PlacedCake placedCake = placement.profile().placedCakes().remove(placement.slot());
        World world = placedCake.worldName() == null ? null : Bukkit.getWorld(placedCake.worldName());
        Block block = world == null ? null : world.getBlockAt(placedCake.x(), placedCake.y(), placedCake.z());
        if (block != null) {
            block.setType(Material.AIR, false);
        }
        customItems.definition(placement.definition().itemId())
                .map(customItems::createItem)
                .ifPresent(itemStack -> giveOrDrop(player, itemStack));
        text.send(player, "commands.cake-picked-up", List.of(TextService.parsed("cake", placement.definition().displayName())));
        return true;
    }

    public void clear(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        profile.cakeBuffs().clear();
        text.send(player, "commands.cake-cleared");
    }

    public void sendSummary(Player player) {
        if (!enabled()) {
            text.send(player, "commands.cake-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        cleanupExpired(profile);
        if (profile.cakeBuffs().isEmpty()) {
            text.send(player, "commands.cake-summary-empty");
            return;
        }
        text.send(player, "commands.cake-summary-header");
        profile.cakeBuffs().entrySet().stream()
                .filter(entry -> entry.getValue() > System.currentTimeMillis())
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> definition(entry.getKey()).ifPresent(definition -> text.send(player, "commands.cake-summary-line", List.of(
                        TextService.parsed("cake", definition.displayName()),
                        TextService.raw("remaining", formatDuration((entry.getValue() - System.currentTimeMillis()) / 1000L))
                ))));
    }

    public void sendPlaced(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.placedCakes().isEmpty()) {
            text.send(player, "commands.cake-placed-empty");
            return;
        }
        text.send(player, "commands.cake-placed-header");
        for (int index = 0; index < profile.placedCakes().size(); index++) {
            PlacedCake placedCake = profile.placedCakes().get(index);
            CakeDefinition definition = definition(placedCake.id()).orElse(null);
            String displayName = definition == null ? placedCake.id() : definition.displayName();
            text.send(player, "commands.cake-placed-line", List.of(
                    TextService.raw("slot", Integer.toString(index + 1)),
                    TextService.parsed("cake", displayName),
                    TextService.parsed("location", locationLabel(placedCake))
            ));
        }
    }

    public Map<String, Double> activeStats(SkyBlockProfile profile) {
        Map<String, Double> stats = new HashMap<>();
        cleanupExpired(profile);
        long now = System.currentTimeMillis();
        for (Map.Entry<String, Long> active : profile.cakeBuffs().entrySet()) {
            if (active.getValue() <= now) {
                continue;
            }
            CakeDefinition definition = definition(active.getKey()).orElse(null);
            if (definition == null) {
                continue;
            }
            for (Map.Entry<String, Double> stat : definition.stats().entrySet()) {
                String normalized = StatSnapshot.normalize(stat.getKey());
                stats.put(normalized, stats.getOrDefault(normalized, 0.0D) + stat.getValue());
            }
        }
        return stats;
    }

    public String formatDuration(long seconds) {
        long remaining = Math.max(0L, seconds);
        long days = remaining / 86_400L;
        remaining %= 86_400L;
        long hours = remaining / 3600L;
        remaining %= 3600L;
        long minutes = remaining / 60L;
        if (days > 0L) {
            return text.rawMessage("cakes.duration-days")
                    .replace("<days>", Long.toString(days))
                    .replace("<hours>", Long.toString(hours));
        }
        if (hours > 0L) {
            return text.rawMessage("cakes.duration-hours")
                    .replace("<hours>", Long.toString(hours))
                    .replace("<minutes>", Long.toString(minutes));
        }
        return text.rawMessage("cakes.duration-minutes")
                .replace("<minutes>", Long.toString(minutes));
    }

    private void cleanupExpired(SkyBlockProfile profile) {
        long now = System.currentTimeMillis();
        profile.cakeBuffs().entrySet().removeIf(entry -> entry.getValue() <= now || !definitions.containsKey(entry.getKey()));
    }

    private int maxPlaced() {
        return Math.max(1, configService.cakes().getInt("settings.max-placed-per-profile", 64));
    }

    private String locationLabel(PlacedCake placedCake) {
        return text.rawMessage("cakes.location")
                .replace("<world>", placedCake.worldName())
                .replace("<x>", Integer.toString(placedCake.x()))
                .replace("<y>", Integer.toString(placedCake.y()))
                .replace("<z>", Integer.toString(placedCake.z()));
    }

    private Map<String, Double> stats(ConfigurationSection section) {
        Map<String, Double> stats = new HashMap<>();
        if (section == null) {
            return stats;
        }
        for (String key : section.getKeys(false)) {
            stats.put(StatSnapshot.normalize(key), section.getDouble(key, 0.0D));
        }
        return stats;
    }

    private void giveOrDrop(Player player, ItemStack itemStack) {
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }
}
