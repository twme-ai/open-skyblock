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
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class SlayerService {
    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final TextService text;
    private final ProfileManager profiles;
    private final EconomyService economy;
    private final SkillService skills;
    private final MobService mobs;
    private final NamespacedKey bossOwnerKey;
    private final NamespacedKey bossSlayerKey;
    private final NamespacedKey bossTierKey;
    private final Map<String, SlayerDefinition> definitions = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private boolean bossBarEnabled = true;
    private long bossTimeoutMillis = 300_000L;
    private BossBar.Color bossBarColor = BossBar.Color.RED;
    private BossBar.Overlay bossBarOverlay = BossBar.Overlay.PROGRESS;

    public SlayerService(JavaPlugin plugin, ConfigService configService, TextService text, ProfileManager profiles, EconomyService economy, SkillService skills, MobService mobs) {
        this.plugin = plugin;
        this.configService = configService;
        this.text = text;
        this.profiles = profiles;
        this.economy = economy;
        this.skills = skills;
        this.mobs = mobs;
        this.bossOwnerKey = new NamespacedKey(plugin, "slayer_boss_owner");
        this.bossSlayerKey = new NamespacedKey(plugin, "slayer_boss_id");
        this.bossTierKey = new NamespacedKey(plugin, "slayer_boss_tier");
    }

    public void reload() {
        definitions.clear();
        ConfigurationSection settings = configService.slayers().getConfigurationSection("settings");
        this.bossTimeoutMillis = Math.max(10L, settings == null ? 300L : settings.getLong("boss-timeout-seconds", 300L)) * 1000L;
        this.bossBarEnabled = settings == null || settings.getBoolean("boss-bar-enabled", true);
        this.bossBarColor = parseBossBarColor(settings == null ? "RED" : settings.getString("boss-bar-color", "RED"));
        this.bossBarOverlay = parseBossBarOverlay(settings == null ? "PROGRESS" : settings.getString("boss-bar-overlay", "PROGRESS"));
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

    public boolean isSlayerBoss(Entity entity) {
        return entity != null && entity.getPersistentDataContainer().has(bossOwnerKey, PersistentDataType.STRING);
    }

    public boolean shouldGrantMobRewards(Player killer, Entity entity) {
        if (!isSlayerBoss(entity)) {
            return true;
        }
        return killer != null && bossOwnerId(entity)
                .filter(killer.getUniqueId()::equals)
                .isPresent();
    }

    public void handleKill(Player killer, SkyBlockMobDefinition killedMob, LivingEntity killedEntity) {
        if (!enabled() || killedMob == null || killedEntity == null) {
            return;
        }
        if (isSlayerBoss(killedEntity)) {
            handleBossDeath(killer, killedMob, killedEntity);
            return;
        }
        if (killer == null) {
            return;
        }
        SkyBlockProfile profile = profiles.profile(killer);
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
            if (active.bossEntityId() == null && killedMob.id().equalsIgnoreCase(tier.bossMobId())) {
                complete(killer, profile, definition, tier);
            }
            return;
        }
        if (!definition.mobIds().contains(killedMob.id())) {
            return;
        }
        active.progressXp(active.progressXp() + Math.max(0.0D, killedMob.skillXp()));
        if (active.progressXp() >= tier.requiredXp()) {
            spawnBoss(killer, active, definition, tier, killedEntity.getLocation());
            return;
        }
        text.send(killer, "commands.slayer-progress", List.of(
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
        LivingEntity entity = mobs.spawn(location, boss);
        long expiresAt = System.currentTimeMillis() + bossTimeoutMillis;
        tagBoss(entity, player, definition, tier);
        active.progressXp(tier.requiredXp());
        active.bossSpawned(true);
        active.bossEntityId(entity.getUniqueId());
        active.bossExpiresAtMillis(expiresAt);
        showOrUpdateBossBar(player, entity, definition, tier, expiresAt);
        profiles.save(player);
        text.send(player, "commands.slayer-boss-spawned", List.of(
                TextService.parsed("slayer", definition.displayName()),
                TextService.raw("tier", Integer.toString(tier.tier())),
                TextService.parsed("boss", boss.displayName())
        ));
    }

    private void complete(Player player, SkyBlockProfile profile, SlayerDefinition definition, SlayerTierDefinition tier) {
        ActiveSlayerQuest active = profile.activeSlayer();
        if (active != null && active.bossEntityId() != null) {
            hideBossBar(active.bossEntityId());
        }
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

    public void refreshBossBarNextTick(Entity entity) {
        if (!isSlayerBoss(entity)) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (entity instanceof LivingEntity livingEntity && livingEntity.isValid() && !livingEntity.isDead()) {
                updateBossBar(livingEntity);
            }
        });
    }

    public void tickBosses() {
        if (!enabled()) {
            hideAllBossBars();
            return;
        }
        long now = System.currentTimeMillis();
        boolean saveAll = false;
        for (SkyBlockProfile profile : profiles.loadedProfiles()) {
            ActiveSlayerQuest active = profile.activeSlayer();
            if (active == null || !active.bossSpawned()) {
                continue;
            }
            if (active.bossExpiresAtMillis() <= 0L) {
                active.bossExpiresAtMillis(now + bossTimeoutMillis);
                saveAll = true;
            }
            Entity entity = active.bossEntityId() == null ? null : Bukkit.getEntity(active.bossEntityId());
            if (entity instanceof LivingEntity livingEntity && entity.isValid() && !entity.isDead()) {
                updateBossBar(livingEntity, profile, active);
            }
            if (active.bossExpiresAtMillis() > 0L && now >= active.bossExpiresAtMillis()) {
                if (entity != null && entity.isValid()) {
                    entity.remove();
                }
                if (active.bossEntityId() != null) {
                    hideBossBar(active.bossEntityId());
                }
                profile.activeSlayer(null);
                Player player = plugin.getServer().getPlayer(profile.uniqueId());
                if (player != null) {
                    text.send(player, "commands.slayer-boss-expired");
                    profiles.save(player);
                } else {
                    saveAll = true;
                }
            }
        }
        if (saveAll) {
            profiles.saveAll();
        }
    }

    public void playerJoined(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        ActiveSlayerQuest active = profile.activeSlayer();
        if (active == null || !active.bossSpawned() || active.bossEntityId() == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(active.bossEntityId());
        if (entity instanceof LivingEntity livingEntity && livingEntity.isValid() && !livingEntity.isDead()) {
            updateBossBar(livingEntity, profile, active);
        }
    }

    public void playerQuit(Player player) {
        SkyBlockProfile profile = profiles.profile(player);
        ActiveSlayerQuest active = profile.activeSlayer();
        if (active == null || active.bossEntityId() == null) {
            return;
        }
        BossBar bossBar = bossBars.get(active.bossEntityId());
        if (bossBar != null) {
            player.hideBossBar(bossBar);
        }
    }

    public void shutdown() {
        hideAllBossBars();
    }

    private void handleBossDeath(Player killer, SkyBlockMobDefinition killedMob, LivingEntity killedEntity) {
        UUID ownerId = bossOwnerId(killedEntity).orElse(null);
        hideBossBar(killedEntity.getUniqueId());
        if (ownerId == null) {
            return;
        }
        if (killer != null && !ownerId.equals(killer.getUniqueId())) {
            text.send(killer, "commands.slayer-boss-not-yours", List.of(TextService.raw("player", ownerName(ownerId))));
        }
        Player owner = plugin.getServer().getPlayer(ownerId);
        SkyBlockProfile profile = owner == null ? profiles.profile(ownerId) : profiles.profile(owner);
        if (profile == null) {
            return;
        }
        ActiveSlayerQuest active = profile.activeSlayer();
        SlayerDefinition definition = active == null ? null : definition(active.slayerId()).orElse(null);
        SlayerTierDefinition tier = definition == null ? null : definition.tiers().get(active.tier());
        if (active == null || definition == null || tier == null || !matchesActiveBoss(active, tier, killedEntity, killedMob)) {
            return;
        }
        if (owner == null) {
            profile.activeSlayer(null);
            profiles.saveAll();
            return;
        }
        complete(owner, profile, definition, tier);
    }

    private boolean matchesActiveBoss(ActiveSlayerQuest active, SlayerTierDefinition tier, LivingEntity entity, SkyBlockMobDefinition killedMob) {
        if (!active.bossSpawned() || !killedMob.id().equalsIgnoreCase(tier.bossMobId())) {
            return false;
        }
        return active.bossEntityId() == null || active.bossEntityId().equals(entity.getUniqueId());
    }

    private void tagBoss(LivingEntity entity, Player player, SlayerDefinition definition, SlayerTierDefinition tier) {
        entity.getPersistentDataContainer().set(bossOwnerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        entity.getPersistentDataContainer().set(bossSlayerKey, PersistentDataType.STRING, definition.id());
        entity.getPersistentDataContainer().set(bossTierKey, PersistentDataType.INTEGER, tier.tier());
    }

    private Optional<UUID> bossOwnerId(Entity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        String raw = entity.getPersistentDataContainer().get(bossOwnerKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(raw));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    private String ownerName(UUID ownerId) {
        SkyBlockProfile profile = profiles.profile(ownerId);
        if (profile == null || profile.playerName() == null || profile.playerName().isBlank()) {
            return ownerId.toString();
        }
        return profile.playerName();
    }

    private void showOrUpdateBossBar(Player player, LivingEntity entity, SlayerDefinition definition, SlayerTierDefinition tier, long expiresAt) {
        if (!bossBarEnabled) {
            return;
        }
        BossBar bossBar = bossBars.computeIfAbsent(entity.getUniqueId(), ignored -> BossBar.bossBar(Component.empty(), 1.0F, bossBarColor, bossBarOverlay));
        bossBar.color(bossBarColor);
        bossBar.overlay(bossBarOverlay);
        bossBar.progress(healthProgress(entity));
        bossBar.name(bossBarName(entity, definition, tier, expiresAt));
        player.showBossBar(bossBar);
    }

    private void updateBossBar(LivingEntity entity) {
        UUID ownerId = bossOwnerId(entity).orElse(null);
        if (ownerId == null) {
            return;
        }
        SkyBlockProfile profile = profiles.profile(ownerId);
        ActiveSlayerQuest active = profile == null ? null : profile.activeSlayer();
        if (profile == null || active == null) {
            return;
        }
        updateBossBar(entity, profile, active);
    }

    private void updateBossBar(LivingEntity entity, SkyBlockProfile profile, ActiveSlayerQuest active) {
        if (!bossBarEnabled || active.bossEntityId() == null) {
            hideBossBar(entity.getUniqueId());
            return;
        }
        SlayerDefinition definition = definition(active.slayerId()).orElse(null);
        SlayerTierDefinition tier = definition == null ? null : definition.tiers().get(active.tier());
        Player player = plugin.getServer().getPlayer(profile.uniqueId());
        if (definition == null || tier == null || player == null) {
            return;
        }
        showOrUpdateBossBar(player, entity, definition, tier, active.bossExpiresAtMillis());
    }

    private Component bossBarName(LivingEntity entity, SlayerDefinition definition, SlayerTierDefinition tier, long expiresAt) {
        SkyBlockMobDefinition boss = mobs.definition(entity).orElse(null);
        String bossName = boss == null ? definition.bossName() : boss.displayName();
        return text.deserialize(text.rawMessage("slayers.boss-bar-title"), List.of(
                TextService.parsed("boss", bossName),
                TextService.parsed("slayer", definition.displayName()),
                TextService.raw("tier", Integer.toString(tier.tier())),
                TextService.raw("health", text.formatNumber(Math.max(0.0D, entity.getHealth()))),
                TextService.raw("max_health", text.formatNumber(maxHealth(entity))),
                TextService.raw("time", Long.toString(remainingSeconds(expiresAt)))
        ));
    }

    private float healthProgress(LivingEntity entity) {
        double maximum = maxHealth(entity);
        if (maximum <= 0.0D) {
            return 0.0F;
        }
        return (float) Math.max(0.0D, Math.min(1.0D, entity.getHealth() / maximum));
    }

    private double maxHealth(LivingEntity entity) {
        AttributeInstance attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            return Math.max(1.0D, entity.getHealth());
        }
        return Math.max(1.0D, attribute.getValue());
    }

    private long remainingSeconds(long expiresAt) {
        if (expiresAt <= 0L) {
            return 0L;
        }
        return Math.max(0L, (long) Math.ceil((expiresAt - System.currentTimeMillis()) / 1000.0D));
    }

    private void hideBossBar(UUID entityId) {
        BossBar bossBar = bossBars.remove(entityId);
        if (bossBar == null) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.hideBossBar(bossBar);
        }
    }

    private void hideAllBossBars() {
        for (UUID entityId : List.copyOf(bossBars.keySet())) {
            hideBossBar(entityId);
        }
    }

    private BossBar.Color parseBossBarColor(String value) {
        if (value == null || value.isBlank()) {
            return BossBar.Color.RED;
        }
        try {
            return BossBar.Color.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BossBar.Color.RED;
        }
    }

    private BossBar.Overlay parseBossBarOverlay(String value) {
        if (value == null || value.isBlank()) {
            return BossBar.Overlay.PROGRESS;
        }
        try {
            return BossBar.Overlay.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return BossBar.Overlay.PROGRESS;
        }
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
