package io.github.openskyblock.fishingfestival;

import io.github.openskyblock.calendar.CalendarService;
import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.mayor.MayorService;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.seacreature.SeaCreatureDefinition;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class FishingFestivalService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final MayorService mayors;
    private final CalendarService calendar;
    private final Map<String, SeaCreatureDefinition> sharks = new HashMap<>();
    private boolean requireMayorPerk = true;
    private String mayorModifier = "fishing_festival_enabled";
    private int startDay = 1;
    private int durationDays = 3;
    private double sharkRollChance = 100.0D;
    private double skyBlockXpPerShark = 0.25D;
    private long saveIntervalCatches = 1L;

    public FishingFestivalService(ConfigService configService, TextService text, ProfileManager profiles, MayorService mayors, CalendarService calendar) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.mayors = mayors;
        this.calendar = calendar;
    }

    public void reload() {
        requireMayorPerk = configService.fishingFestival().getBoolean("settings.require-mayor-perk", true);
        mayorModifier = configService.fishingFestival().getString("settings.mayor-modifier", "fishing_festival_enabled").toLowerCase(Locale.ROOT).replace('-', '_');
        startDay = Math.max(1, configService.fishingFestival().getInt("settings.start-day", 1));
        durationDays = Math.max(1, configService.fishingFestival().getInt("settings.duration-days", 3));
        sharkRollChance = Math.max(0.0D, Math.min(100.0D, configService.fishingFestival().getDouble("settings.shark-roll-chance", 100.0D)));
        skyBlockXpPerShark = Math.max(0.0D, configService.fishingFestival().getDouble("settings.skyblock-xp-per-shark", 0.25D));
        saveIntervalCatches = Math.max(1L, configService.fishingFestival().getLong("settings.save-interval-catches", 1L));
        loadSharks();
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.fishing-festival", true);
    }

    public boolean eventActive() {
        return enabled() && mayorActive() && windowActive();
    }

    public List<String> sharkIds() {
        return sharks().stream().map(SeaCreatureDefinition::id).toList();
    }

    public List<SeaCreatureDefinition> sharks() {
        return sharks.values().stream()
                .sorted(Comparator.comparingInt(SeaCreatureDefinition::requiredFishingLevel).thenComparing(SeaCreatureDefinition::id))
                .toList();
    }

    public Optional<SeaCreatureDefinition> selectShark(int fishingLevel) {
        if (!eventActive() || ThreadLocalRandom.current().nextDouble(100.0D) >= sharkRollChance) {
            return Optional.empty();
        }
        List<SeaCreatureDefinition> eligible = sharks().stream()
                .filter(shark -> fishingLevel >= shark.requiredFishingLevel())
                .toList();
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        double totalWeight = eligible.stream().mapToDouble(SeaCreatureDefinition::weight).sum();
        if (totalWeight <= 0.0D) {
            return Optional.of(eligible.get(ThreadLocalRandom.current().nextInt(eligible.size())));
        }
        double target = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cursor = 0.0D;
        for (SeaCreatureDefinition shark : eligible) {
            cursor += shark.weight();
            if (target <= cursor) {
                return Optional.of(shark);
            }
        }
        return Optional.of(eligible.getLast());
    }

    public void recordSharkCatch(Player player, SeaCreatureDefinition shark, double seaCreatureChance) {
        SkyBlockProfile profile = profiles.profile(player);
        profile.addFishingFestivalSharkCatch(shark.id(), 1L);
        if (profile.fishingFestivalSharkCatches() % saveIntervalCatches == 0L) {
            profiles.save(player);
        }
        text.send(player, "commands.fishing-festival-shark-hooked", List.of(
                TextService.parsed("shark", shark.displayName()),
                TextService.raw("chance", text.formatNumber(seaCreatureChance)),
                TextService.raw("catches", text.formatNumber(profile.fishingFestivalSharkCatches())),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile)))
        ));
    }

    public void sendStatus(Player player) {
        if (!enabled()) {
            text.send(player, "commands.fishing-festival-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.fishing-festival-status", List.of(
                TextService.parsed("active", eventActive() ? "<green>Active</green>" : "<red>Inactive</red>"),
                TextService.parsed("mayor", mayorActive() ? "<green>Enabled</green>" : "<red>Missing</red>"),
                TextService.raw("window", windowStatus()),
                TextService.raw("sharks", text.formatNumber(profile.fishingFestivalSharkCatches())),
                TextService.raw("skyblock_xp", text.formatNumber(skyBlockXp(profile)))
        ));
    }

    public void sendSharks(Player player) {
        if (!enabled()) {
            text.send(player, "commands.fishing-festival-disabled");
            return;
        }
        if (sharks.isEmpty()) {
            text.send(player, "commands.fishing-festival-sharks-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.fishing-festival-sharks-header", List.of(TextService.raw("roll_chance", text.formatNumber(sharkRollChance))));
        for (SeaCreatureDefinition shark : sharks()) {
            text.send(player, "commands.fishing-festival-sharks-line", List.of(
                    TextService.raw("id", shark.id()),
                    TextService.parsed("shark", shark.displayName()),
                    TextService.raw("mob", shark.mobId()),
                    TextService.raw("required_level", Integer.toString(shark.requiredFishingLevel())),
                    TextService.raw("weight", text.formatNumber(shark.weight())),
                    TextService.raw("catches", text.formatNumber(profile.fishingFestivalSharkCatches(shark.id())))
            ));
        }
    }

    public double skyBlockXp(SkyBlockProfile profile) {
        if (!enabled()) {
            return 0.0D;
        }
        return profile.fishingFestivalSharkCatches() * skyBlockXpPerShark;
    }

    private boolean mayorActive() {
        return !requireMayorPerk || mayors.modifier(mayorModifier) > 0.0D;
    }

    private boolean windowActive() {
        int day = calendar.currentDate().day();
        return day >= startDay && day < startDay + durationDays;
    }

    private String windowStatus() {
        int day = calendar.currentDate().day();
        if (windowActive()) {
            int remaining = startDay + durationDays - day;
            return "active, " + remaining + " SB day(s) remaining";
        }
        if (day < startDay) {
            return "starts in " + (startDay - day) + " SB day(s)";
        }
        return "starts next SkyBlock month";
    }

    private void loadSharks() {
        sharks.clear();
        ConfigurationSection section = configService.fishingFestival().getConfigurationSection("sharks");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection shark = section.getConfigurationSection(id);
            if (shark == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            sharks.put(normalized, new SeaCreatureDefinition(
                    normalized,
                    shark.getString("display-name", normalized),
                    shark.getString("mob", normalized).toUpperCase(Locale.ROOT),
                    Math.max(0, shark.getInt("required-fishing-level", 0)),
                    Math.max(0.0D, shark.getDouble("weight", 1.0D))
            ));
        }
    }
}
