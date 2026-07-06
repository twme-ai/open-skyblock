package io.github.openskyblock.cookie;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.museum.MuseumService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class CookieService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CustomItemService customItems;
    private final Map<String, FameRankDefinition> fameRanks = new HashMap<>();
    private MuseumService museumService;
    private String boosterCookieItemId = "BOOSTER_COOKIE";
    private long durationSeconds = 345_600L;
    private long baseBits = 4_800L;
    private long accrualIntervalSeconds = 1_800L;
    private long bitsPerInterval = 250L;
    private double famePerBitSpent = 1.0D;

    public CookieService(ConfigService configService, TextService text, ProfileManager profiles, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.customItems = customItems;
    }

    public void museumService(MuseumService museumService) {
        this.museumService = museumService;
    }

    public void reload() {
        this.boosterCookieItemId = configService.cookies().getString("settings.booster-cookie-item", "BOOSTER_COOKIE").toUpperCase(Locale.ROOT);
        this.durationSeconds = Math.max(60L, configService.cookies().getLong("settings.duration-seconds", 345_600L));
        this.baseBits = Math.max(0L, configService.cookies().getLong("settings.base-bits", 4_800L));
        this.accrualIntervalSeconds = Math.max(1L, configService.cookies().getLong("settings.accrual-interval-seconds", 1_800L));
        this.bitsPerInterval = Math.max(1L, configService.cookies().getLong("settings.bits-per-interval", 250L));
        this.famePerBitSpent = Math.max(0.0D, configService.cookies().getDouble("settings.fame-per-bit-spent", 1.0D));
        loadFameRanks();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.cookies", true);
    }

    public long tickIntervalTicks() {
        return Math.max(20L, configService.cookies().getLong("settings.tick-interval-ticks", 200L));
    }

    public boolean isBoosterCookie(ItemStack itemStack) {
        return customItems.definition(itemStack)
                .map(CustomItemDefinition::id)
                .filter(id -> id.equalsIgnoreCase(boosterCookieItemId))
                .isPresent();
    }

    public boolean consumeHeld(Player player) {
        if (!enabled()) {
            text.send(player, "commands.cookie-disabled");
            return false;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!isBoosterCookie(held)) {
            text.send(player, "commands.cookie-held-missing");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        long now = System.currentTimeMillis();
        long start = Math.max(now, profile.cookieBuffExpiresAtMillis());
        long expiresAt = start + durationSeconds * 1000L;
        long addedBits = bitsGranted(profile);
        profile.cookieBuffExpiresAtMillis(expiresAt);
        profile.addBitsAvailable(addedBits);
        profile.addCookieConsumed();
        if (profile.bitsLastAccrualMillis() <= 0L || profile.bitsLastAccrualMillis() > now) {
            profile.bitsLastAccrualMillis(now);
        }
        held.setAmount(held.getAmount() - 1);
        if (held.getAmount() <= 0) {
            player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        }
        profiles.save(player);
        text.send(player, "commands.cookie-consumed", cookiePlaceholders(profile, addedBits));
        return true;
    }

    public void tickOnlinePlayers() {
        if (!enabled()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            accrueBits(player, true);
        }
    }

    public void sendSummary(Player player) {
        if (!enabled()) {
            text.send(player, "commands.cookie-disabled");
            return;
        }
        accrueBits(player, false);
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.cookie-summary", cookiePlaceholders(profile, 0L));
    }

    public boolean spendBits(Player player, long amount) {
        if (!enabled()) {
            text.send(player, "commands.cookie-disabled");
            return false;
        }
        if (amount <= 0L) {
            text.send(player, "errors.invalid-number");
            return false;
        }
        accrueBits(player, false);
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.bits() < amount) {
            text.send(player, "commands.bits-not-enough", List.of(
                    TextService.raw("bits", text.formatNumber(profile.bits())),
                    TextService.raw("amount", text.formatNumber(amount))
            ));
            return false;
        }
        profile.bits(profile.bits() - amount);
        profile.addFameXp(amount * famePerBitSpent);
        profiles.save(player);
        text.send(player, "commands.bits-spent", List.of(
                TextService.raw("amount", text.formatNumber(amount)),
                TextService.raw("bits", text.formatNumber(profile.bits())),
                TextService.raw("fame", text.formatNumber(profile.fameXp())),
                TextService.parsed("rank", fameRank(profile).displayName())
        ));
        return true;
    }

    public void sendFame(Player player) {
        if (!enabled()) {
            text.send(player, "commands.cookie-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        FameRankDefinition rank = fameRank(profile);
        text.send(player, "commands.fame-summary", List.of(
                TextService.parsed("rank", rank.displayName()),
                TextService.raw("fame", text.formatNumber(profile.fameXp())),
                TextService.raw("bits_multiplier", percent(totalBitsMultiplier(profile))),
                TextService.raw("museum_bonus", percent(museumBitsBonus(profile))),
                TextService.raw("cookies", text.formatNumber(profile.cookiesConsumed()))
        ));
        for (FameRankDefinition fameRank : fameRanks()) {
            text.send(player, "commands.fame-rank-line", List.of(
                    TextService.parsed("rank", fameRank.displayName()),
                    TextService.raw("required", text.formatNumber(fameRank.requiredFame())),
                    TextService.raw("bits_multiplier", percent(fameRank.bitsMultiplier()))
            ));
        }
    }

    private void accrueBits(Player player, boolean notify) {
        SkyBlockProfile profile = profiles.profile(player);
        long now = System.currentTimeMillis();
        if (profile.cookieBuffExpiresAtMillis() <= now || profile.bitsAvailable() <= 0L) {
            profile.bitsLastAccrualMillis(now);
            return;
        }
        long last = profile.bitsLastAccrualMillis();
        if (last <= 0L || last > now) {
            profile.bitsLastAccrualMillis(now);
            return;
        }
        long intervals = (now - last) / (accrualIntervalSeconds * 1000L);
        if (intervals <= 0L) {
            return;
        }
        long amount = Math.min(profile.bitsAvailable(), intervals * bitsPerInterval);
        if (amount <= 0L) {
            return;
        }
        profile.addBits(amount);
        profile.bitsAvailable(profile.bitsAvailable() - amount);
        profile.bitsLastAccrualMillis(last + intervals * accrualIntervalSeconds * 1000L);
        profiles.save(player);
        if (notify) {
            text.send(player, "commands.bits-earned", List.of(
                    TextService.raw("amount", text.formatNumber(amount)),
                    TextService.raw("bits", text.formatNumber(profile.bits())),
                    TextService.raw("available", text.formatNumber(profile.bitsAvailable()))
            ));
        }
    }

    private void loadFameRanks() {
        fameRanks.clear();
        ConfigurationSection section = configService.cookies().getConfigurationSection("fame-ranks");
        if (section == null) {
            fameRanks.put("NEW_PLAYER", new FameRankDefinition("NEW_PLAYER", "<gray>New Player</gray>", 0.0D, 1.0D));
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection rank = section.getConfigurationSection(id);
            if (rank == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            fameRanks.put(normalized, new FameRankDefinition(
                    normalized,
                    rank.getString("display-name", normalized),
                    Math.max(0.0D, rank.getDouble("required-fame", 0.0D)),
                    Math.max(0.0D, rank.getDouble("bits-multiplier", 1.0D))
            ));
        }
    }

    private List<FameRankDefinition> fameRanks() {
        return fameRanks.values().stream()
                .sorted(Comparator.comparingDouble(FameRankDefinition::requiredFame))
                .toList();
    }

    private FameRankDefinition fameRank(SkyBlockProfile profile) {
        FameRankDefinition current = fameRanks().isEmpty()
                ? new FameRankDefinition("NEW_PLAYER", "<gray>New Player</gray>", 0.0D, 1.0D)
                : fameRanks().getFirst();
        for (FameRankDefinition rank : fameRanks()) {
            if (profile.fameXp() >= rank.requiredFame()) {
                current = rank;
            }
        }
        return current;
    }

    private long bitsGranted(SkyBlockProfile profile) {
        return Math.max(0L, Math.round(baseBits * totalBitsMultiplier(profile)));
    }

    private double totalBitsMultiplier(SkyBlockProfile profile) {
        return Math.max(0.0D, fameRank(profile).bitsMultiplier() + museumBitsBonus(profile));
    }

    private double museumBitsBonus(SkyBlockProfile profile) {
        return museumService == null ? 0.0D : museumService.bitsMultiplier(profile);
    }

    private List<TextService.TextPlaceholder> cookiePlaceholders(SkyBlockProfile profile, long addedBits) {
        return List.of(
                TextService.raw("remaining", cookieRemaining(profile)),
                TextService.raw("bits", text.formatNumber(profile.bits())),
                TextService.raw("available", text.formatNumber(profile.bitsAvailable())),
                TextService.raw("added_available", text.formatNumber(addedBits)),
                TextService.raw("fame", text.formatNumber(profile.fameXp())),
                TextService.parsed("rank", fameRank(profile).displayName()),
                TextService.raw("bits_multiplier", percent(totalBitsMultiplier(profile))),
                TextService.raw("cookies", text.formatNumber(profile.cookiesConsumed()))
        );
    }

    private String cookieRemaining(SkyBlockProfile profile) {
        long now = System.currentTimeMillis();
        if (profile.cookieBuffExpiresAtMillis() <= now) {
            return text.rawMessage("cookies.inactive");
        }
        return formatDuration((profile.cookieBuffExpiresAtMillis() - now + 999L) / 1000L);
    }

    private String formatDuration(long totalSeconds) {
        if (totalSeconds >= 86400L) {
            long days = totalSeconds / 86400L;
            long hours = (totalSeconds % 86400L) / 3600L;
            return days + "d " + hours + "h";
        }
        if (totalSeconds >= 3600L) {
            long hours = totalSeconds / 3600L;
            long minutes = (totalSeconds % 3600L) / 60L;
            return hours + "h " + minutes + "m";
        }
        if (totalSeconds >= 60L) {
            long minutes = totalSeconds / 60L;
            long seconds = totalSeconds % 60L;
            return minutes + "m " + seconds + "s";
        }
        return totalSeconds + "s";
    }

    private String percent(double value) {
        return text.formatNumber(value * 100.0D) + "%";
    }
}
