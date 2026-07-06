package io.github.openskyblock.seacreature;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.mayor.MayorService;
import io.github.openskyblock.mob.MobService;
import io.github.openskyblock.mob.SkyBlockMobDefinition;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.SkillService;
import io.github.openskyblock.service.SkillType;
import io.github.openskyblock.stats.StatService;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class SeaCreatureService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final SkillService skills;
    private final StatService stats;
    private final MobService mobs;
    private final MayorService mayors;
    private final Map<String, SeaCreatureDefinition> definitions = new HashMap<>();
    private double baseChance = 20.0D;
    private boolean removeCaughtItem = true;

    public SeaCreatureService(ConfigService configService, TextService text, ProfileManager profiles, SkillService skills, StatService stats, MobService mobs, MayorService mayors) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.skills = skills;
        this.stats = stats;
        this.mobs = mobs;
        this.mayors = mayors;
    }

    public void reload() {
        this.baseChance = Math.max(0.0D, Math.min(100.0D, configService.seaCreatures().getDouble("settings.base-chance", 20.0D)));
        this.removeCaughtItem = configService.seaCreatures().getBoolean("settings.remove-caught-item-on-spawn", true);
        definitions.clear();
        ConfigurationSection section = configService.seaCreatures().getConfigurationSection("creatures");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection creature = section.getConfigurationSection(id);
            if (creature == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            definitions.put(normalized, new SeaCreatureDefinition(
                    normalized,
                    creature.getString("display-name", normalized),
                    creature.getString("mob", normalized).toUpperCase(Locale.ROOT),
                    Math.max(0, creature.getInt("required-fishing-level", 0)),
                    Math.max(0.0D, creature.getDouble("weight", 1.0D))
            ));
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.sea-creatures", true);
    }

    public boolean removeCaughtItem() {
        return removeCaughtItem;
    }

    public List<SeaCreatureDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparingInt(SeaCreatureDefinition::requiredFishingLevel).thenComparing(SeaCreatureDefinition::id))
                .toList();
    }

    public List<String> creatureIds() {
        return definitions().stream().map(SeaCreatureDefinition::id).toList();
    }

    public boolean trySpawn(Player player, Location hookLocation) {
        if (!enabled() || player == null || hookLocation == null || hookLocation.getWorld() == null) {
            return false;
        }
        SkyBlockProfile profile = profiles.profile(player);
        int fishingLevel = skills.level(SkillType.FISHING, profile.skillXp(SkillType.FISHING));
        double chance = effectiveChance(player);
        if (ThreadLocalRandom.current().nextDouble(100.0D) >= chance) {
            return false;
        }
        SeaCreatureDefinition creature = selectCreature(fishingLevel).orElse(null);
        if (creature == null) {
            return false;
        }
        SkyBlockMobDefinition mob = mobs.definition(creature.mobId()).orElse(null);
        if (mob == null) {
            text.send(player, "commands.sea-creature-missing-mob", List.of(TextService.raw("mob", creature.mobId())));
            return false;
        }
        mobs.spawn(spawnLocation(player, hookLocation), mob);
        text.send(player, "commands.sea-creature-spawned", List.of(
                TextService.parsed("creature", creature.displayName()),
                TextService.raw("chance", text.formatNumber(chance))
        ));
        return true;
    }

    public void sendSummary(Player player) {
        if (!enabled()) {
            text.send(player, "commands.sea-creature-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        int fishingLevel = skills.level(SkillType.FISHING, profile.skillXp(SkillType.FISHING));
        text.send(player, "commands.sea-creature-summary", List.of(
                TextService.raw("chance", text.formatNumber(effectiveChance(player))),
                TextService.raw("fishing_level", Integer.toString(fishingLevel)),
                TextService.raw("eligible", Integer.toString(eligibleCreatures(fishingLevel).size()))
        ));
    }

    public void sendList(Player player) {
        if (!enabled()) {
            text.send(player, "commands.sea-creature-disabled");
            return;
        }
        if (definitions.isEmpty()) {
            text.send(player, "commands.sea-creature-empty");
            return;
        }
        text.send(player, "commands.sea-creature-list-header");
        for (SeaCreatureDefinition creature : definitions()) {
            text.send(player, "commands.sea-creature-list-line", creaturePlaceholders(creature));
        }
    }

    private Optional<SeaCreatureDefinition> selectCreature(int fishingLevel) {
        List<SeaCreatureDefinition> eligible = eligibleCreatures(fishingLevel);
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        double totalWeight = eligible.stream().mapToDouble(SeaCreatureDefinition::weight).sum();
        if (totalWeight <= 0.0D) {
            return Optional.of(eligible.get(ThreadLocalRandom.current().nextInt(eligible.size())));
        }
        double target = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cursor = 0.0D;
        for (SeaCreatureDefinition creature : eligible) {
            cursor += creature.weight();
            if (target <= cursor) {
                return Optional.of(creature);
            }
        }
        return Optional.of(eligible.getLast());
    }

    private List<SeaCreatureDefinition> eligibleCreatures(int fishingLevel) {
        return definitions().stream()
                .filter(creature -> fishingLevel >= creature.requiredFishingLevel())
                .toList();
    }

    private double effectiveChance(Player player) {
        double chance = baseChance + Math.max(0.0D, stats.snapshot(player).stat("sea_creature_chance"));
        chance += Math.max(0.0D, mayors.modifier("sea_creature_chance_bonus"));
        return Math.max(0.0D, Math.min(100.0D, chance));
    }

    private Location spawnLocation(Player player, Location hookLocation) {
        Location location = hookLocation.clone();
        location.setY(Math.max(location.getY(), player.getLocation().getY()));
        return location;
    }

    private List<TextService.TextPlaceholder> creaturePlaceholders(SeaCreatureDefinition creature) {
        return List.of(
                TextService.raw("id", creature.id()),
                TextService.parsed("creature", creature.displayName()),
                TextService.raw("mob", creature.mobId()),
                TextService.raw("required_level", Integer.toString(creature.requiredFishingLevel())),
                TextService.raw("weight", text.formatNumber(creature.weight()))
        );
    }
}
