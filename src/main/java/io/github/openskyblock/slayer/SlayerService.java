package io.github.openskyblock.slayer;

import io.github.openskyblock.config.ConfigService;
import io.github.openskyblock.config.TextService;
import io.github.openskyblock.economy.EconomyService;
import io.github.openskyblock.mob.MobService;
import io.github.openskyblock.mob.SkyBlockMobDefinition;
import io.github.openskyblock.profile.ActiveSlayerQuest;
import io.github.openskyblock.profile.ProfileManager;
import io.github.openskyblock.profile.SkyBlockProfile;
import io.github.openskyblock.service.SkillService;
import io.github.openskyblock.service.SkillType;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

public final class SlayerService {
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final SkillService skills;
    private final MobService mobs;
    private final Map<String, SlayerDefinition> definitions = new HashMap<>();

    public SlayerService(ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, SkillService skills, MobService mobs) {
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.skills = skills;
        this.mobs = mobs;
    }

    public void reload() {
        definitions.clear();
        ConfigurationSection section = configService.slayers().getConfigurationSection("slayers");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection slayerSection = section.getConfigurationSection(id);
            if (slayerSection == null) {
                continue;
            }
            String normalized = id.toUpperCase(Locale.ROOT);
            Set<String> mobIds = new HashSet<>();
            for (String mobId : slayerSection.getStringList("mobs")) {
                if (mobId != null && !mobId.isBlank()) {
                    mobIds.add(mobId.toUpperCase(Locale.ROOT));
                }
            }
            definitions.put(normalized, new SlayerDefinition(
                    normalized,
                    slayerSection.getString("display-name", id),
                    slayerSection.getString("boss-name", id),
                    Set.copyOf(mobIds),
                    readTiers(slayerSection.getConfigurationSection("tiers"))
            ));
        }
    }

    public boolean enabled() {
        return configService.main().getBoolean("features.slayers", true);
    }

    public Optional<SlayerDefinition> definition(String id) {
        if (id == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(definitions.get(id.toUpperCase(Locale.ROOT)));
    }

    public List<SlayerDefinition> definitions() {
        return definitions.values().stream()
                .sorted(Comparator.comparing(SlayerDefinition::id))
                .toList();
    }

    public List<String> slayerIds() {
        return definitions().stream().map(SlayerDefinition::id).toList();
    }

    public void start(Player player, String slayerId, int tierNumber) {
        if (!enabled()) {
            text.send(player, "commands.slayer-disabled");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.activeSlayer() != null) {
            text.send(player, "commands.slayer-already-active");
            return;
        }
        SlayerDefinition definition = definition(slayerId).orElse(null);
        if (definition == null) {
            text.send(player, "commands.slayer-unknown", List.of(TextService.raw("slayer", slayerId)));
            return;
        }
        SlayerTierDefinition tier = definition.tiers().get(tierNumber);
        if (tier == null) {
            text.send(player, "commands.slayer-unknown-tier", List.of(
                    TextService.parsed("slayer", definition.displayName()),
                    TextService.raw("tier", Integer.toString(tierNumber))
            ));
            return;
        }
        if (!economy.spendPurse(player, tier.cost())) {
            text.send(player, "commands.slayer-no-money", List.of(TextService.raw("cost", text.formatNumber(tier.cost()))));
            return;
        }
        profile.activeSlayer(new ActiveSlayerQuest(definition.id(), tier.tier(), 0.0D, false));
        profiles.save(player);
        text.send(player, "commands.slayer-started", List.of(
                TextService.parsed("slayer", definition.displayName()),
                TextService.raw("tier", Integer.toString(tier.tier())),
                TextService.raw("required_xp", text.formatNumber(tier.requiredXp())),
                TextService.raw("cost", text.formatNumber(tier.cost()))
        ));
    }

    public void cancel(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        if (profile.activeSlayer() == null) {
            text.send(player, "commands.slayer-no-active");
            return;
        }
        profile.activeSlayer(null);
        profiles.save(player);
        text.send(player, "commands.slayer-cancelled");
    }

    public void status(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        ActiveSlayerQuest active = profile.activeSlayer();
        if (active == null) {
            text.send(player, "commands.slayer-no-active");
            return;
        }
        SlayerDefinition definition = definition(active.slayerId()).orElse(null);
        SlayerTierDefinition tier = definition == null ? null : definition.tiers().get(active.tier());
        if (definition == null || tier == null) {
            text.send(player, "commands.slayer-invalid-active");
            return;
        }
        text.send(player, "commands.slayer-status", List.of(
                TextService.parsed("slayer", definition.displayName()),
                TextService.raw("tier", Integer.toString(tier.tier())),
                TextService.raw("progress", text.formatNumber(Math.min(active.progressXp(), tier.requiredXp()))),
                TextService.raw("required_xp", text.formatNumber(tier.requiredXp())),
                TextService.parsed("phase", active.bossSpawned() ? text.rawMessage("slayers.phase-boss") : text.rawMessage("slayers.phase-progress"))
        ));
    }

    public void sendList(Player player) {
        if (!enabled()) {
            text.send(player, "commands.slayer-disabled");
            return;
        }
        List<SlayerDefinition> values = definitions();
        if (values.isEmpty()) {
            text.send(player, "commands.slayer-empty");
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        text.send(player, "commands.slayer-list-header");
        for (SlayerDefinition definition : values) {
            double xp = profile.slayerXp().getOrDefault(definition.id(), 0.0D);
            text.send(player, "commands.slayer-list-line", List.of(
                    TextService.raw("id", definition.id()),
                    TextService.parsed("slayer", definition.displayName()),
                    TextService.raw("tiers", Integer.toString(definition.tiers().size())),
                    TextService.raw("xp", text.formatNumber(xp))
            ));
        }
    }

    public void handleKill(Player player, SkyBlockMobDefinition killedMob, Location deathLocation) {
        if (!enabled() || killedMob == null) {
            return;
        }
        SkyBlockProfile profile = profiles.profile(player);
        ActiveSlayerQuest active = profile.activeSlayer();
        if (active == null) {
            return;
        }
        SlayerDefinition definition = definition(active.slayerId()).orElse(null);
        SlayerTierDefinition tier = definition == null ? null : definition.tiers().get(active.tier());
        if (definition == null || tier == null) {
            return;
        }
        if (active.bossSpawned()) {
            if (killedMob.id().equalsIgnoreCase(tier.bossMobId())) {
                complete(player, profile, definition, tier);
            }
            return;
        }
        if (!definition.mobIds().contains(killedMob.id())) {
            return;
        }
        active.progressXp(active.progressXp() + Math.max(0.0D, killedMob.skillXp()));
        if (active.progressXp() >= tier.requiredXp()) {
            spawnBoss(player, active, definition, tier, deathLocation);
            return;
        }
        text.send(player, "commands.slayer-progress", List.of(
                TextService.parsed("slayer", definition.displayName()),
                TextService.raw("progress", text.formatNumber(active.progressXp())),
                TextService.raw("required_xp", text.formatNumber(tier.requiredXp()))
        ));
    }

    private void spawnBoss(Player player, ActiveSlayerQuest active, SlayerDefinition definition, SlayerTierDefinition tier, Location location) {
        SkyBlockMobDefinition boss = mobs.definition(tier.bossMobId()).orElse(null);
        if (boss == null) {
            text.send(player, "commands.slayer-boss-missing", List.of(TextService.raw("mob", tier.bossMobId())));
            return;
        }
        mobs.spawn(location, boss);
        active.progressXp(tier.requiredXp());
        active.bossSpawned(true);
        profiles.save(player);
        text.send(player, "commands.slayer-boss-spawned", List.of(
                TextService.parsed("slayer", definition.displayName()),
                TextService.raw("tier", Integer.toString(tier.tier())),
                TextService.parsed("boss", boss.displayName())
        ));
    }

    private void complete(Player player, SkyBlockProfile profile, SlayerDefinition definition, SlayerTierDefinition tier) {
        profile.activeSlayer(null);
        profile.slayerXp().put(definition.id(), profile.slayerXp().getOrDefault(definition.id(), 0.0D) + tier.slayerXp());
        if (tier.rewardSkill() != null && tier.rewardSkillXp() > 0.0D) {
            skills.addXp(player, tier.rewardSkill(), tier.rewardSkillXp());
        }
        if (tier.coins() > 0.0D) {
            economy.addPurse(player, tier.coins());
            text.send(player, "progression.coins", List.of(TextService.raw("coins", text.formatNumber(tier.coins()))));
        }
        profiles.save(player);
        text.send(player, "commands.slayer-completed", List.of(
                TextService.parsed("slayer", definition.displayName()),
                TextService.raw("tier", Integer.toString(tier.tier())),
                TextService.raw("slayer_xp", text.formatNumber(tier.slayerXp()))
        ));
    }

    private Map<Integer, SlayerTierDefinition> readTiers(ConfigurationSection section) {
        Map<Integer, SlayerTierDefinition> tiers = new LinkedHashMap<>();
        if (section == null) {
            return tiers;
        }
        for (String key : section.getKeys(false)) {
            ConfigurationSection tierSection = section.getConfigurationSection(key);
            if (tierSection == null) {
                continue;
            }
            try {
                int tier = Integer.parseInt(key);
                SkillType rewardSkill = SkillType.fromKey(tierSection.getString("reward-skill", "COMBAT")).orElse(SkillType.COMBAT);
                String bossMobId = Optional.ofNullable(tierSection.getString("boss-mob", ""))
                        .orElse("")
                        .toUpperCase(Locale.ROOT);
                tiers.put(tier, new SlayerTierDefinition(
                        tier,
                        Math.max(0.0D, tierSection.getDouble("cost", 0.0D)),
                        Math.max(1.0D, tierSection.getDouble("required-xp", 100.0D)),
                        bossMobId,
                        Math.max(0.0D, tierSection.getDouble("slayer-xp", 0.0D)),
                        rewardSkill,
                        Math.max(0.0D, tierSection.getDouble("reward-skill-xp", 0.0D)),
                        Math.max(0.0D, tierSection.getDouble("coins", 0.0D))
                ));
            } catch (NumberFormatException ignored) {
                // Invalid tier keys are skipped so one typo does not break the full Slayer file.
            }
        }
        return Map.copyOf(tiers);
    }
}
