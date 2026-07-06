package io.github.openskyblock.potion;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.stats.StatSnapshot;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class PotionService {
    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final Map<String, SkyBlockPotionEffectDefinition> effects = new LinkedHashMap<>();
    private final Map<String, PotionBundleDefinition> bundles = new LinkedHashMap<>();
    private final Map<UUID, Long> lastTickMillis = new HashMap<>();
    private long refreshTicks = 200L;
    private long effectRefreshSeconds = 30L;
    private boolean pauseOnPrivateIslands = true;

    public PotionService(JavaPlugin plugin, ConfigService configService, TextService text, ProfileManager profiles) {
        this.plugin = plugin;
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
    }

    public void reload() {
        effects.clear();
        bundles.clear();
        this.refreshTicks = Math.max(20L, configService.potions().getLong("settings.refresh-ticks", 200L));
        this.effectRefreshSeconds = Math.max(2L, configService.potions().getLong("settings.effect-refresh-seconds", 30L));
        this.pauseOnPrivateIslands = configService.potions().getBoolean("settings.pause-on-private-islands", true);
        loadEffects();
        loadBundles();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            playerJoined(player);
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.potion-effects", true);
    }

    public long refreshTicks() {
        return refreshTicks;
    }

    public List<SkyBlockPotionEffectDefinition> definitions() {
        return effects.values().stream()
                .sorted(Comparator.comparing(SkyBlockPotionEffectDefinition::id))
                .toList();
    }

    public List<PotionBundleDefinition> bundles() {
        return bundles.values().stream()
                .sorted(Comparator.comparing(PotionBundleDefinition::id))
                .toList();
    }

    public Optional<PotionBundleDefinition> bundle(String bundleId) {
        if (bundleId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(bundles.get(bundleId.toUpperCase(Locale.ROOT)));
    }

    public Optional<PotionBundleDefinition> bundleForItem(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return Optional.empty();
        }
        return bundles.values().stream()
                .filter(bundle -> bundle.itemId() != null && bundle.itemId().equalsIgnoreCase(itemId))
                .findFirst();
    }

    public boolean activateItem(Player player, String itemId, ItemStack itemStack) {
        PotionBundleDefinition bundle = bundleForItem(itemId).orElse(null);
        if (bundle == null) {
            return false;
        }
        boolean activated = activateBundle(player, bundle.id());
        if (activated && itemStack != null) {
            itemStack.setAmount(Math.max(0, itemStack.getAmount() - 1));
        }
        return true;
    }

    public boolean activateBundle(Player player, String bundleId) {
        if (!enabled()) {
            text.send(player, "commands.potion-disabled");
            return false;
        }
        PotionBundleDefinition bundle = bundle(bundleId).orElse(null);
        if (bundle == null) {
            text.send(player, "commands.potion-unknown-bundle", List.of(TextService.raw("bundle", bundleId == null ? "" : bundleId)));
            return false;
        }
        if (bundle.effectIds().isEmpty()) {
            text.send(player, "commands.potion-empty-bundle", List.of(TextService.parsed("bundle", bundle.displayName())));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        long remaining = bundleRemaining(profile, bundle);
        if (bundle.maxDurationSeconds() > 0L && !canAddDuration(profile, bundle)) {
            text.send(player, "commands.potion-maxed", List.of(
                    TextService.parsed("bundle", bundle.displayName()),
                    TextService.raw("remaining", formatDuration(remaining))
            ));
            return false;
        }
        long added = 0L;
        for (String effectId : bundle.effectIds()) {
            SkyBlockPotionEffectDefinition effect = effects.get(effectId.toUpperCase(Locale.ROOT));
            if (effect == null) {
                continue;
            }
            long current = profile.potionEffects().getOrDefault(effect.id(), 0L);
            long next = current + bundle.durationSeconds();
            if (bundle.maxDurationSeconds() > 0L) {
                next = Math.min(next, bundle.maxDurationSeconds());
            }
            profile.potionEffects().put(effect.id(), Math.max(0L, next));
            added = Math.max(added, Math.max(0L, next - current));
        }
        if (added <= 0L) {
            text.send(player, "commands.potion-empty-bundle", List.of(TextService.parsed("bundle", bundle.displayName())));
            return false;
        }
        lastTickMillis.put(player.getUniqueId(), System.currentTimeMillis());
        applyActiveEffects(player);
        text.send(player, "commands.potion-activated", List.of(
                TextService.parsed("bundle", bundle.displayName()),
                TextService.raw("duration", formatDuration(added)),
                TextService.raw("remaining", formatDuration(bundleRemaining(profile, bundle)))
        ));
        return true;
    }

    public void clear(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        profile.potionEffects().clear();
        for (SkyBlockPotionEffectDefinition effect : effects.values()) {
            if (effect.type() != null) {
                player.removePotionEffect(effect.type());
            }
        }
        text.send(player, "commands.potion-cleared");
    }

    public void sendSummary(Player player) {
        if (!enabled()) {
            text.send(player, "commands.potion-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        cleanupExpired(profile);
        if (profile.potionEffects().isEmpty()) {
            text.send(player, "commands.potion-summary-empty");
            return;
        }
        text.send(player, "commands.potion-summary-header");
        profile.potionEffects().entrySet().stream()
                .filter(entry -> entry.getValue() > 0L)
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    SkyBlockPotionEffectDefinition effect = effects.get(entry.getKey());
                    if (effect != null) {
                        text.send(player, "commands.potion-summary-line", List.of(
                                TextService.parsed("effect", effect.displayName()),
                                TextService.raw("remaining", formatDuration(entry.getValue()))
                        ));
                    }
                });
    }

    public Map<String, Double> activeStats(SkyBlockProfile profile) {
        Map<String, Double> stats = new HashMap<>();
        cleanupExpired(profile);
        for (Map.Entry<String, Long> active : profile.potionEffects().entrySet()) {
            if (active.getValue() <= 0L) {
                continue;
            }
            SkyBlockPotionEffectDefinition effect = effects.get(active.getKey());
            if (effect == null) {
                continue;
            }
            for (Map.Entry<String, Double> stat : effect.stats().entrySet()) {
                String normalized = StatSnapshot.normalize(stat.getKey());
                stats.put(normalized, stats.getOrDefault(normalized, 0.0D) + stat.getValue());
            }
        }
        return stats;
    }

    public void playerJoined(Player player) {
        lastTickMillis.put(player.getUniqueId(), System.currentTimeMillis());
        if (enabled()) {
            applyActiveEffects(player);
        }
    }

    public void playerQuit(Player player) {
        tickPlayer(player);
        lastTickMillis.remove(player.getUniqueId());
    }

    public void tickOnlinePlayers() {
        if (!enabled()) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            tickPlayer(player);
        }
    }

    public String formatDuration(long seconds) {
        long remaining = Math.max(0L, seconds);
        long hours = remaining / 3600L;
        remaining %= 3600L;
        long minutes = remaining / 60L;
        long secs = remaining % 60L;
        if (hours > 0L) {
            return text.rawMessage("potions.duration-hours")
                    .replace("<hours>", Long.toString(hours))
                    .replace("<minutes>", Long.toString(minutes));
        }
        if (minutes > 0L) {
            return text.rawMessage("potions.duration-minutes")
                    .replace("<minutes>", Long.toString(minutes))
                    .replace("<seconds>", Long.toString(secs));
        }
        return text.rawMessage("potions.duration-seconds")
                .replace("<seconds>", Long.toString(secs));
    }

    private void tickPlayer(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        long now = System.currentTimeMillis();
        long last = lastTickMillis.getOrDefault(player.getUniqueId(), now);
        lastTickMillis.put(player.getUniqueId(), now);
        long elapsedSeconds = Math.max(0L, (now - last) / 1000L);
        if (elapsedSeconds > 0L && !timerPaused(player)) {
            decrement(profile, elapsedSeconds);
        }
        cleanupExpired(profile);
        applyActiveEffects(player);
    }

    private boolean timerPaused(Player player) {
        if (!pauseOnPrivateIslands) {
            return false;
        }
        String prefix = configService.main().getString("islands.world-prefix", "openskyblock_island_");
        String worldName = player.getWorld().getName();
        return prefix != null && !prefix.isBlank() && worldName.startsWith(prefix);
    }

    private void decrement(SkyBlockProfile profile, long elapsedSeconds) {
        for (Map.Entry<String, Long> entry : new HashMap<>(profile.potionEffects()).entrySet()) {
            profile.potionEffects().put(entry.getKey(), Math.max(0L, entry.getValue() - elapsedSeconds));
        }
    }

    private void cleanupExpired(SkyBlockProfile profile) {
        profile.potionEffects().entrySet().removeIf(entry -> entry.getValue() <= 0L || !effects.containsKey(entry.getKey()));
    }

    private void applyActiveEffects(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        cleanupExpired(profile);
        for (Map.Entry<String, Long> entry : profile.potionEffects().entrySet()) {
            SkyBlockPotionEffectDefinition effect = effects.get(entry.getKey());
            if (effect == null || effect.type() == null || entry.getValue() <= 0L) {
                continue;
            }
            int durationTicks = (int) Math.min(Integer.MAX_VALUE, Math.max(1L, Math.min(effectRefreshSeconds, entry.getValue()) * 20L));
            player.addPotionEffect(new PotionEffect(
                    effect.type(),
                    durationTicks,
                    effect.amplifier(),
                    effect.ambient(),
                    effect.particles(),
                    effect.icon()
            ), true);
        }
    }

    private long bundleRemaining(SkyBlockProfile profile, PotionBundleDefinition bundle) {
        return bundle.effectIds().stream()
                .map(effectId -> effectId.toUpperCase(Locale.ROOT))
                .filter(effects::containsKey)
                .mapToLong(effectId -> profile.potionEffects().getOrDefault(effectId, 0L))
                .max()
                .orElse(0L);
    }

    private boolean canAddDuration(SkyBlockProfile profile, PotionBundleDefinition bundle) {
        if (bundle.maxDurationSeconds() <= 0L) {
            return true;
        }
        for (String effectId : bundle.effectIds()) {
            SkyBlockPotionEffectDefinition effect = effects.get(effectId.toUpperCase(Locale.ROOT));
            if (effect != null && profile.potionEffects().getOrDefault(effect.id(), 0L) < bundle.maxDurationSeconds()) {
                return true;
            }
        }
        return false;
    }

    private void loadEffects() {
        ConfigurationSection section = configService.potions().getConfigurationSection("effects");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection effectSection = section.getConfigurationSection(id);
            if (effectSection == null) {
                continue;
            }
            String normalizedId = id.toUpperCase(Locale.ROOT);
            effects.put(normalizedId, new SkyBlockPotionEffectDefinition(
                    normalizedId,
                    effectSection.getString("display-name", id),
                    potionType(effectSection.getString("potion-effect", id)),
                    Math.max(0, effectSection.getInt("amplifier", 0)),
                    effectSection.getBoolean("ambient", false),
                    effectSection.getBoolean("particles", false),
                    effectSection.getBoolean("icon", true),
                    stats(effectSection.getConfigurationSection("stats"))
            ));
        }
    }

    private void loadBundles() {
        ConfigurationSection section = configService.potions().getConfigurationSection("bundles");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection bundleSection = section.getConfigurationSection(id);
            if (bundleSection == null) {
                continue;
            }
            String normalizedId = id.toUpperCase(Locale.ROOT);
            long duration = Math.max(1L, bundleSection.getLong("duration-seconds", 43_200L));
            long maxDuration = Math.max(0L, bundleSection.getLong("max-duration-seconds", duration));
            bundles.put(normalizedId, new PotionBundleDefinition(
                    normalizedId,
                    bundleSection.getString("display-name", id),
                    bundleSection.getString("item-id", ""),
                    duration,
                    maxDuration,
                    bundleSection.getStringList("effects").stream()
                            .map(effectId -> effectId.toUpperCase(Locale.ROOT))
                            .toList()
            ));
        }
    }

    private PotionEffectType potionType(String raw) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("none")) {
            return null;
        }
        String normalized = raw.toLowerCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        NamespacedKey key = NamespacedKey.fromString(normalized);
        if (key == null) {
            key = NamespacedKey.minecraft(normalized);
        }
        PotionEffectType type = Registry.EFFECT.get(key);
        if (type != null) {
            return type;
        }
        return Registry.EFFECT.match(raw);
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
}
