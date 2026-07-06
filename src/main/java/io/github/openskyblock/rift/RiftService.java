package io.github.openskyblock.rift;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.CustomItemDefinition;
import io.github.openskyblock.service.CustomItemService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class RiftService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final CustomItemService customItems;
    private final Map<String, RiftZoneDefinition> zones = new HashMap<>();
    private final Map<String, RiftTimecharmDefinition> timecharms = new HashMap<>();
    private final Map<String, RiftSoulDefinition> souls = new HashMap<>();
    private final Map<String, RiftOrbDefinition> orbs = new HashMap<>();
    private long baseRiftTimeSeconds = 600L;
    private int soulsPerExchange = 4;
    private long motesPerSoulExchange = 250L;
    private long riftTimeSecondsPerExchange = 15L;
    private double skyBlockXpPerSoulExchange = 2.0D;

    public RiftService(ConfigService configService, TextService text, ProfileManager profiles, CustomItemService customItems) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.customItems = customItems;
    }

    public void reload() {
        baseRiftTimeSeconds = Math.max(1L, configService.rift().getLong("settings.base-rift-time-seconds", 600L));
        soulsPerExchange = Math.max(1, configService.rift().getInt("settings.souls-per-exchange", 4));
        motesPerSoulExchange = Math.max(0L, configService.rift().getLong("settings.motes-per-soul-exchange", 250L));
        riftTimeSecondsPerExchange = Math.max(0L, configService.rift().getLong("settings.rift-time-seconds-per-exchange", 15L));
        skyBlockXpPerSoulExchange = Math.max(0.0D, configService.rift().getDouble("settings.skyblock-xp-per-soul-exchange", 2.0D));
        loadZones();
        loadTimecharms();
        loadSouls();
        loadOrbs();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.rift", true);
    }

    public List<String> zoneIds() {
        return zoneDefinitions().stream().map(RiftZoneDefinition::id).toList();
    }

    public List<String> timecharmIds() {
        return timecharmDefinitions().stream().map(RiftTimecharmDefinition::id).toList();
    }

    public List<String> soulIds() {
        return soulDefinitions().stream().map(RiftSoulDefinition::id).toList();
    }

    public List<String> orbIds() {
        return orbDefinitions().stream().map(RiftOrbDefinition::id).toList();
    }

    public Optional<RiftZoneDefinition> zone(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(zones.get(id.toUpperCase(Locale.ROOT)));
    }

    public Optional<RiftTimecharmDefinition> timecharm(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(timecharms.get(id.toUpperCase(Locale.ROOT)));
    }

    public Optional<RiftSoulDefinition> soul(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(souls.get(id.toUpperCase(Locale.ROOT)));
    }

    public Optional<RiftOrbDefinition> orb(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(orbs.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<RiftZoneDefinition> zoneDefinitions() {
        return zones.values().stream()
                .sorted(Comparator.comparingInt(RiftZoneDefinition::requiredTimecharmCount).thenComparing(RiftZoneDefinition::id))
                .toList();
    }

    public List<RiftTimecharmDefinition> timecharmDefinitions() {
        return timecharms.values().stream()
                .sorted(Comparator.<RiftTimecharmDefinition>comparingInt(timecharm -> zone(timecharm.zoneId()).map(RiftZoneDefinition::requiredTimecharmCount).orElse(0)).thenComparing(RiftTimecharmDefinition::id))
                .toList();
    }

    public List<RiftSoulDefinition> soulDefinitions() {
        return souls.values().stream()
                .sorted(Comparator.comparingInt(RiftSoulDefinition::requiredTimecharmCount).thenComparing(RiftSoulDefinition::id))
                .toList();
    }

    public List<RiftOrbDefinition> orbDefinitions() {
        return orbs.values().stream()
                .sorted(Comparator.comparing(RiftOrbDefinition::id))
                .toList();
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.rift-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.rift-status", List.of(
                TextService.raw("motes", text.formatNumber(profile.riftMotes())),
                TextService.raw("max_time", formatDuration(maxRiftTimeSeconds(profile))),
                TextService.raw("entries", text.formatNumber(profile.riftEntries())),
                TextService.raw("spent_time", formatDuration(profile.riftTimeSpentSeconds())),
                TextService.raw("orbs", text.formatNumber(profile.riftOrbsCollected())),
                TextService.raw("timecharms", Integer.toString(profile.riftTimecharms().size())),
                TextService.raw("total_timecharms", Integer.toString(timecharms.size())),
                TextService.raw("souls", Integer.toString(profile.riftSouls().size())),
                TextService.raw("total_souls", Integer.toString(souls.size())),
                TextService.raw("available_exchanges", Long.toString(availableSoulExchanges(profile))),
                TextService.raw("soul_exchanges", text.formatNumber(profile.riftSoulExchanges())),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile)))
        ));
    }

    public void sendGuide(Player player) {
        if (!enabled()) {
            text.send(player, "commands.rift-disabled");
            return;
        }
        if (timecharms.isEmpty()) {
            text.send(player, "commands.rift-empty-timecharms");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.rift-guide-header");
        for (RiftTimecharmDefinition timecharm : timecharmDefinitions()) {
            text.send(player, "commands.rift-guide-line", timecharmPlaceholders(profile, timecharm));
        }
    }

    public void sendZones(Player player) {
        if (!enabled()) {
            text.send(player, "commands.rift-disabled");
            return;
        }
        if (zones.isEmpty()) {
            text.send(player, "commands.rift-empty-zones");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.rift-zone-header");
        for (RiftZoneDefinition zone : zoneDefinitions()) {
            text.send(player, "commands.rift-zone-line", zonePlaceholders(profile, zone));
        }
    }

    public void sendSouls(Player player) {
        if (!enabled()) {
            text.send(player, "commands.rift-disabled");
            return;
        }
        if (souls.isEmpty()) {
            text.send(player, "commands.rift-empty-souls");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.rift-soul-header", List.of(
                TextService.raw("available_exchanges", Long.toString(availableSoulExchanges(profile))),
                TextService.raw("souls_per_exchange", Integer.toString(soulsPerExchange))
        ));
        for (RiftSoulDefinition soul : soulDefinitions()) {
            text.send(player, "commands.rift-soul-line", soulPlaceholders(profile, soul));
        }
    }

    public boolean enter(Player player, long requestedSeconds) {
        if (!enabled()) {
            text.send(player, "commands.rift-disabled");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        long maxSeconds = maxRiftTimeSeconds(profile);
        long visitSeconds = requestedSeconds <= 0L ? maxSeconds : Math.min(requestedSeconds, maxSeconds);
        profile.addRiftEntries(1L);
        profile.addRiftTimeSpentSeconds(visitSeconds);
        profiles.save(player);
        text.send(player, "commands.rift-entered", List.of(
                TextService.raw("visit_time", formatDuration(visitSeconds)),
                TextService.raw("max_time", formatDuration(maxSeconds)),
                TextService.raw("entries", text.formatNumber(profile.riftEntries()))
        ));
        return true;
    }

    public boolean gatherOrb(Player player, String orbId, long amount) {
        if (!enabled()) {
            text.send(player, "commands.rift-disabled");
            return false;
        }
        if (amount <= 0L) {
            text.send(player, "errors.invalid-number");
            return false;
        }
        RiftOrbDefinition orb = orb(orbId).orElse(null);
        if (orb == null) {
            text.send(player, "commands.rift-unknown-orb", List.of(TextService.raw("orb", orbId == null ? "" : orbId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        long motes = orb.motes() * amount;
        long recoveredTime = orb.riftTimeSeconds() * amount;
        profile.addRiftMotes(motes);
        profile.addRiftOrbsCollected(amount);
        profiles.save(player);
        text.send(player, "commands.rift-orb-collected", List.of(
                TextService.raw("amount", text.formatNumber(amount)),
                TextService.parsed("orb", orb.displayName()),
                TextService.raw("motes", text.formatNumber(motes)),
                TextService.raw("time", formatDuration(recoveredTime)),
                TextService.raw("balance", text.formatNumber(profile.riftMotes()))
        ));
        return true;
    }

    public boolean collectSoul(Player player, String soulId) {
        if (!enabled()) {
            text.send(player, "commands.rift-disabled");
            return false;
        }
        RiftSoulDefinition soul = soul(soulId).orElse(null);
        if (soul == null) {
            text.send(player, "commands.rift-unknown-soul", List.of(TextService.raw("soul", soulId == null ? "" : soulId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.hasRiftSoul(soul.id())) {
            text.send(player, "commands.rift-soul-already", soulPlaceholders(profile, soul));
            return false;
        }
        if (!soulUnlocked(profile, soul)) {
            text.send(player, "commands.rift-soul-locked", soulPlaceholders(profile, soul));
            return false;
        }
        profile.addRiftSoul(soul.id());
        if (soul.moteReward() > 0L) {
            profile.addRiftMotes(soul.moteReward());
        }
        profiles.save(player);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(soulPlaceholders(profile, soul));
        placeholders.add(TextService.raw("available_exchanges", Long.toString(availableSoulExchanges(profile))));
        text.send(player, "commands.rift-soul-found", placeholders);
        if (availableSoulExchanges(profile) > 0L) {
            text.send(player, "commands.rift-exchange-ready", List.of(TextService.raw("available_exchanges", Long.toString(availableSoulExchanges(profile)))));
        }
        return true;
    }

    public boolean exchangeSouls(Player player) {
        if (!enabled()) {
            text.send(player, "commands.rift-disabled");
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        long exchanges = availableSoulExchanges(profile);
        if (exchanges <= 0L) {
            text.send(player, "commands.rift-exchange-none", summaryPlaceholders(profile));
            return false;
        }
        long motes = exchanges * motesPerSoulExchange;
        profile.addRiftSoulExchanges(exchanges);
        profile.addRiftMotes(motes);
        profiles.save(player);
        text.send(player, "commands.rift-exchanged", List.of(
                TextService.raw("exchanges", Long.toString(exchanges)),
                TextService.raw("motes", text.formatNumber(motes)),
                TextService.raw("time_bonus", formatDuration(exchanges * riftTimeSecondsPerExchange)),
                TextService.raw("skyblock_xp", text.formatNumber(exchanges * skyBlockXpPerSoulExchange)),
                TextService.raw("total_exchanges", text.formatNumber(profile.riftSoulExchanges())),
                TextService.raw("balance", text.formatNumber(profile.riftMotes()))
        ));
        return true;
    }

    public boolean claimTimecharm(Player player, String timecharmId) {
        if (!enabled()) {
            text.send(player, "commands.rift-disabled");
            return false;
        }
        RiftTimecharmDefinition timecharm = timecharm(timecharmId).orElse(null);
        if (timecharm == null) {
            text.send(player, "commands.rift-unknown-timecharm", List.of(TextService.raw("timecharm", timecharmId == null ? "" : timecharmId)));
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.hasRiftTimecharm(timecharm.id())) {
            text.send(player, "commands.rift-timecharm-owned", timecharmPlaceholders(profile, timecharm));
            return false;
        }
        if (!timecharmUnlocked(profile, timecharm)) {
            text.send(player, "commands.rift-timecharm-locked", timecharmPlaceholders(profile, timecharm));
            return false;
        }
        if (profile.riftMotes() < timecharm.moteCost()) {
            text.send(player, "commands.rift-timecharm-no-motes", timecharmPlaceholders(profile, timecharm));
            return false;
        }
        profile.addRiftMotes(-timecharm.moteCost());
        profile.addRiftTimecharm(timecharm.id());
        Optional<String> item = giveTimecharmItem(player, timecharm);
        profiles.save(player);
        List<TextService.TextPlaceholder> placeholders = new ArrayList<>(timecharmPlaceholders(profile, timecharm));
        placeholders.add(TextService.parsed("item", item.orElse("<gray>stored in the Rift Guide</gray>")));
        text.send(player, "commands.rift-timecharm-claimed", placeholders);
        return true;
    }

    public long maxRiftTimeSeconds(SkyBlockProfile profile) {
        long seconds = baseRiftTimeSeconds + profile.riftSoulExchanges() * riftTimeSecondsPerExchange;
        for (String timecharmId : profile.riftTimecharms()) {
            RiftTimecharmDefinition timecharm = timecharms.get(timecharmId.toUpperCase(Locale.ROOT));
            if (timecharm != null) {
                seconds += timecharm.riftTimeBonusSeconds();
            }
        }
        return Math.max(1L, seconds);
    }

    public double skyBlockXp(SkyBlockProfile profile) {
        double xp = profile.riftSoulExchanges() * skyBlockXpPerSoulExchange;
        for (String timecharmId : profile.riftTimecharms()) {
            RiftTimecharmDefinition timecharm = timecharms.get(timecharmId.toUpperCase(Locale.ROOT));
            if (timecharm != null) {
                xp += timecharm.skyBlockXp();
            }
        }
        return Math.max(0.0D, xp);
    }

    private void loadZones() {
        zones.clear();
        ConfigurationSection section = configService.rift().getConfigurationSection("zones");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection zoneSection = section.getConfigurationSection(id);
            if (zoneSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            zones.put(normalized, new RiftZoneDefinition(
                    normalized,
                    zoneSection.getString("display-name", normalized),
                    Math.max(0, zoneSection.getInt("required-timecharm-count", 0)),
                    normalizeList(zoneSection.getStringList("required-timecharms")),
                    Math.max(0L, zoneSection.getLong("entry-cost-seconds", 0L))
            ));
        }
    }

    private void loadTimecharms() {
        timecharms.clear();
        ConfigurationSection section = configService.rift().getConfigurationSection("timecharms");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection timecharmSection = section.getConfigurationSection(id);
            if (timecharmSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            timecharms.put(normalized, new RiftTimecharmDefinition(
                    normalized,
                    timecharmSection.getString("display-name", normalized),
                    timecharmSection.getString("zone", "").toUpperCase(Locale.ROOT),
                    Math.max(0L, timecharmSection.getLong("mote-cost", 0L)),
                    Math.max(0L, timecharmSection.getLong("required-soul-exchanges", 0L)),
                    normalizeList(timecharmSection.getStringList("required-timecharms")),
                    Math.max(0L, timecharmSection.getLong("rift-time-bonus-seconds", 0L)),
                    Math.max(0.0D, timecharmSection.getDouble("skyblock-xp", 0.0D)),
                    timecharmSection.getString("custom-item", "").toUpperCase(Locale.ROOT)
            ));
        }
    }

    private void loadSouls() {
        souls.clear();
        ConfigurationSection section = configService.rift().getConfigurationSection("souls");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection soulSection = section.getConfigurationSection(id);
            if (soulSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            souls.put(normalized, new RiftSoulDefinition(
                    normalized,
                    soulSection.getString("display-name", normalized),
                    soulSection.getString("zone", "").toUpperCase(Locale.ROOT),
                    Math.max(0, soulSection.getInt("required-timecharm-count", 0)),
                    normalizeList(soulSection.getStringList("required-timecharms")),
                    Math.max(0L, soulSection.getLong("mote-reward", 0L))
            ));
        }
    }

    private void loadOrbs() {
        orbs.clear();
        ConfigurationSection section = configService.rift().getConfigurationSection("orbs");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection orbSection = section.getConfigurationSection(id);
            if (orbSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            orbs.put(normalized, new RiftOrbDefinition(
                    normalized,
                    orbSection.getString("display-name", normalized),
                    Math.max(0L, orbSection.getLong("motes", 0L)),
                    Math.max(0L, orbSection.getLong("rift-time-seconds", 0L))
            ));
        }
    }

    private boolean zoneUnlocked(SkyBlockProfile profile, RiftZoneDefinition zone) {
        if (profile.riftTimecharms().size() < zone.requiredTimecharmCount()) {
            return false;
        }
        return profile.riftTimecharms().containsAll(zone.requiredTimecharms());
    }

    private boolean soulUnlocked(SkyBlockProfile profile, RiftSoulDefinition soul) {
        if (profile.riftTimecharms().size() < soul.requiredTimecharmCount()) {
            return false;
        }
        if (!profile.riftTimecharms().containsAll(soul.requiredTimecharms())) {
            return false;
        }
        return soul.zoneId().isBlank() || zone(soul.zoneId()).map(zone -> zoneUnlocked(profile, zone)).orElse(true);
    }

    private boolean timecharmUnlocked(SkyBlockProfile profile, RiftTimecharmDefinition timecharm) {
        if (profile.riftSoulExchanges() < timecharm.requiredSoulExchanges()) {
            return false;
        }
        if (!profile.riftTimecharms().containsAll(timecharm.requiredTimecharms())) {
            return false;
        }
        return timecharm.zoneId().isBlank() || zone(timecharm.zoneId()).map(zone -> zoneUnlocked(profile, zone)).orElse(true);
    }

    private Optional<String> giveTimecharmItem(Player player, RiftTimecharmDefinition timecharm) {
        if (timecharm.customItemId().isBlank()) {
            return Optional.empty();
        }
        CustomItemDefinition definition = customItems.definition(timecharm.customItemId()).orElse(null);
        if (definition == null) {
            return Optional.empty();
        }
        ItemStack itemStack = customItems.createItem(definition);
        player.getInventory().addItem(itemStack).values()
                .forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        return Optional.of(definition.displayName());
    }

    private long availableSoulExchanges(SkyBlockProfile profile) {
        long possible = profile.riftSouls().size() / soulsPerExchange;
        return Math.max(0L, possible - profile.riftSoulExchanges());
    }

    private List<TextService.TextPlaceholder> summaryPlaceholders(SkyBlockProfile profile) {
        return List.of(
                TextService.raw("souls", Integer.toString(profile.riftSouls().size())),
                TextService.raw("total_souls", Integer.toString(souls.size())),
                TextService.raw("souls_per_exchange", Integer.toString(soulsPerExchange)),
                TextService.raw("available_exchanges", Long.toString(availableSoulExchanges(profile))),
                TextService.raw("soul_exchanges", text.formatNumber(profile.riftSoulExchanges())),
                TextService.raw("motes", text.formatNumber(profile.riftMotes())),
                TextService.raw("max_time", formatDuration(maxRiftTimeSeconds(profile))),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile)))
        );
    }

    private List<TextService.TextPlaceholder> zonePlaceholders(SkyBlockProfile profile, RiftZoneDefinition zone) {
        return List.of(
                TextService.raw("id", zone.id()),
                TextService.parsed("zone", zone.displayName()),
                TextService.raw("required_timecharm_count", Integer.toString(zone.requiredTimecharmCount())),
                TextService.raw("required_timecharms", zone.requiredTimecharms().isEmpty() ? "none" : String.join(", ", zone.requiredTimecharms())),
                TextService.raw("entry_cost", formatDuration(zone.entryCostSeconds())),
                TextService.parsed("status", zoneUnlocked(profile, zone) ? "<green>Unlocked</green>" : "<red>Locked</red>")
        );
    }

    private List<TextService.TextPlaceholder> timecharmPlaceholders(SkyBlockProfile profile, RiftTimecharmDefinition timecharm) {
        RiftZoneDefinition zone = zone(timecharm.zoneId()).orElse(null);
        String zoneName = zone == null ? timecharm.zoneId() : zone.displayName();
        String status = profile.hasRiftTimecharm(timecharm.id())
                ? "<green>Claimed</green>"
                : timecharmUnlocked(profile, timecharm) && profile.riftMotes() >= timecharm.moteCost() ? "<yellow>Ready</yellow>" : "<red>Locked</red>";
        return List.of(
                TextService.raw("id", timecharm.id()),
                TextService.parsed("timecharm", timecharm.displayName()),
                TextService.parsed("zone", zoneName),
                TextService.raw("mote_cost", text.formatNumber(timecharm.moteCost())),
                TextService.raw("balance", text.formatNumber(profile.riftMotes())),
                TextService.raw("required_soul_exchanges", text.formatNumber(timecharm.requiredSoulExchanges())),
                TextService.raw("required_timecharms", timecharm.requiredTimecharms().isEmpty() ? "none" : String.join(", ", timecharm.requiredTimecharms())),
                TextService.raw("rift_time_bonus", formatDuration(timecharm.riftTimeBonusSeconds())),
                TextService.raw("skyblock_xp", text.formatNumber(timecharm.skyBlockXp())),
                TextService.parsed("status", status)
        );
    }

    private List<TextService.TextPlaceholder> soulPlaceholders(SkyBlockProfile profile, RiftSoulDefinition soul) {
        RiftZoneDefinition zone = zone(soul.zoneId()).orElse(null);
        String zoneName = zone == null ? soul.zoneId() : zone.displayName();
        return List.of(
                TextService.raw("id", soul.id()),
                TextService.parsed("soul", soul.displayName()),
                TextService.parsed("zone", zoneName),
                TextService.raw("required_timecharm_count", Integer.toString(soul.requiredTimecharmCount())),
                TextService.raw("required_timecharms", soul.requiredTimecharms().isEmpty() ? "none" : String.join(", ", soul.requiredTimecharms())),
                TextService.raw("mote_reward", text.formatNumber(soul.moteReward())),
                TextService.raw("found", Integer.toString(profile.riftSouls().size())),
                TextService.raw("total", Integer.toString(souls.size())),
                TextService.parsed("status", profile.hasRiftSoul(soul.id()) ? "<green>Found</green>" : soulUnlocked(profile, soul) ? "<yellow>Available</yellow>" : "<red>Locked</red>")
        );
    }

    private List<String> normalizeList(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.toUpperCase(Locale.ROOT))
                .toList();
    }

    private String formatDuration(long seconds) {
        long normalized = Math.max(0L, seconds);
        long minutes = normalized / 60L;
        long remainder = normalized % 60L;
        if (minutes <= 0L) {
            return remainder + "s";
        }
        if (remainder == 0L) {
            return minutes + "m";
        }
        return minutes + "m " + remainder + "s";
    }
}
