package io.github.openskyblock.fairysoul;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class FairySoulService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final Map<String, FairySoulDefinition> souls = new HashMap<>();
    private double defaultClaimRadius = 2.5D;
    private int soulsPerExchange = 5;
    private double skyBlockXpPerExchange = 10.0D;
    private int backpackSlotsPerExchange = 1;
    private int maxBackpackSlotBonus = 18;

    public FairySoulService(ConfigService configService, TextService text, ProfileManager profiles) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
    }

    public void reload() {
        defaultClaimRadius = Math.max(0.5D, configService.fairySouls().getDouble("settings.default-claim-radius", 2.5D));
        soulsPerExchange = Math.max(1, configService.fairySouls().getInt("settings.souls-per-exchange", 5));
        skyBlockXpPerExchange = Math.max(0.0D, configService.fairySouls().getDouble("settings.skyblock-xp-per-exchange", 10.0D));
        backpackSlotsPerExchange = Math.max(0, configService.fairySouls().getInt("settings.backpack-slots-per-exchange", 1));
        maxBackpackSlotBonus = Math.max(0, configService.fairySouls().getInt("settings.max-backpack-slot-bonus", 18));
        loadSouls();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.fairy-souls", true);
    }

    public List<FairySoulDefinition> definitions() {
        return souls.values().stream()
                .sorted(Comparator.comparing(FairySoulDefinition::id))
                .toList();
    }

    public boolean tryClaim(Player player, Location clickedLocation) {
        if (!enabled() || player == null || clickedLocation == null || clickedLocation.getWorld() == null) {
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        FairySoulDefinition soul = nearestUnclaimedSoul(profile, clickedLocation).orElse(null);
        if (soul == null) {
            return false;
        }
        profile.addFairySoul(soul.id());
        profiles.save(player);
        text.send(player, "commands.fairy-soul-found", List.of(
                TextService.parsed("soul", soul.displayName()),
                TextService.raw("found", Integer.toString(profile.fairySouls().size())),
                TextService.raw("total", Integer.toString(souls.size())),
                TextService.raw("available_exchanges", Long.toString(availableExchanges(profile)))
        ));
        if (availableExchanges(profile) > 0L) {
            text.send(player, "commands.fairy-soul-exchange-ready", List.of(
                    TextService.raw("available_exchanges", Long.toString(availableExchanges(profile)))
            ));
        }
        return true;
    }

    public boolean exchange(Player player) {
        if (!enabled()) {
            text.send(player, "commands.fairy-soul-disabled");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        long exchanges = availableExchanges(profile);
        if (exchanges <= 0L) {
            text.send(player, "commands.fairy-soul-no-exchange", summaryPlaceholders(profile));
            return false;
        }
        profile.addFairySoulExchanges(exchanges);
        profiles.save(player);
        text.send(player, "commands.fairy-soul-exchanged", List.of(
                TextService.raw("exchanges", Long.toString(exchanges)),
                TextService.raw("skyblock_xp", text.formatNumber(exchanges * skyBlockXpPerExchange)),
                TextService.raw("backpack_slots", Integer.toString(backpackSlotBonus(profile))),
                TextService.raw("total_exchanges", Long.toString(profile.fairySoulExchanges()))
        ));
        return true;
    }

    public void sendSummary(Player player) {
        if (!enabled()) {
            text.send(player, "commands.fairy-soul-disabled");
            return;
        }
        text.send(player, "commands.fairy-soul-summary", summaryPlaceholders(profiles.profile(player)));
    }

    public void sendList(Player player) {
        if (!enabled()) {
            text.send(player, "commands.fairy-soul-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (souls.isEmpty()) {
            text.send(player, "commands.fairy-soul-empty");
            return;
        }
        text.send(player, "commands.fairy-soul-list-header", summaryPlaceholders(profile));
        for (FairySoulDefinition soul : definitions()) {
            text.send(player, "commands.fairy-soul-list-line", List.of(
                    TextService.raw("status", profile.hasFairySoul(soul.id()) ? text.rawMessage("fairy-souls.found-status") : text.rawMessage("fairy-souls.missing-status")),
                    TextService.raw("id", soul.id()),
                    TextService.parsed("soul", soul.displayName()),
                    TextService.raw("world", soul.worldName()),
                    TextService.raw("x", Integer.toString(soul.x())),
                    TextService.raw("y", Integer.toString(soul.y())),
                    TextService.raw("z", Integer.toString(soul.z()))
            ));
        }
    }

    public double skyBlockXp(SkyBlockProfile profile) {
        return profile.fairySoulExchanges() * skyBlockXpPerExchange;
    }

    public int backpackSlotBonus(SkyBlockProfile profile) {
        long bonus = profile.fairySoulExchanges() * (long) backpackSlotsPerExchange;
        return (int) Math.min(maxBackpackSlotBonus, Math.max(0L, bonus));
    }

    public int maxBackpackSlotBonus() {
        return maxBackpackSlotBonus;
    }

    private void loadSouls() {
        souls.clear();
        ConfigurationSection section = configService.fairySouls().getConfigurationSection("souls");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection soul = section.getConfigurationSection(id);
            if (soul == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            souls.put(normalized, new FairySoulDefinition(
                    normalized,
                    soul.getString("display-name", normalized),
                    soul.getString("world", "world"),
                    soul.getInt("x", 0),
                    soul.getInt("y", 64),
                    soul.getInt("z", 0),
                    Math.max(0.5D, soul.getDouble("claim-radius", defaultClaimRadius))
            ));
        }
    }

    private Optional<FairySoulDefinition> nearestUnclaimedSoul(SkyBlockProfile profile, Location location) {
        return definitions().stream()
                .filter(soul -> !profile.hasFairySoul(soul.id()))
                .filter(soul -> soul.worldName().equalsIgnoreCase(location.getWorld().getName()))
                .filter(soul -> distanceSquared(soul, location) <= soul.claimRadius() * soul.claimRadius())
                .min(Comparator.comparingDouble(soul -> distanceSquared(soul, location)));
    }

    private double distanceSquared(FairySoulDefinition soul, Location location) {
        double dx = soul.x() + 0.5D - location.getX();
        double dy = soul.y() + 0.5D - location.getY();
        double dz = soul.z() + 0.5D - location.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private long availableExchanges(SkyBlockProfile profile) {
        long possible = profile.fairySouls().size() / soulsPerExchange;
        return Math.max(0L, possible - profile.fairySoulExchanges());
    }

    private List<TextService.TextPlaceholder> summaryPlaceholders(SkyBlockProfile profile) {
        return List.of(
                TextService.raw("found", Integer.toString(profile.fairySouls().size())),
                TextService.raw("total", Integer.toString(souls.size())),
                TextService.raw("souls_per_exchange", Integer.toString(soulsPerExchange)),
                TextService.raw("available_exchanges", Long.toString(availableExchanges(profile))),
                TextService.raw("exchanges", Long.toString(profile.fairySoulExchanges())),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile))),
                TextService.raw("backpack_slots", Integer.toString(backpackSlotBonus(profile)))
        );
    }
}
